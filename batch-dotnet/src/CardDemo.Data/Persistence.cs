using System.Globalization;
using CardDemo.Domain;
using Npgsql;

namespace CardDemo.Data;

public interface IKsdsPersistence
{
    IEnumerable<byte[]> Load(string dsn, RecordLayout layout);
    void Save(string dsn, RecordLayout layout, IEnumerable<byte[]> records);
}

/// <summary>Fixed-length records in the repository's unloaded-dataset format: one record per line, trailing spaces stripped.</summary>
public sealed class FilePersistence(string datasetDir) : IKsdsPersistence
{
    public string PathOf(string dsn) => Path.Combine(datasetDir, dsn + ".txt");

    public IEnumerable<byte[]> Load(string dsn, RecordLayout layout) => SequentialFile.Read(PathOf(dsn), layout.Length);

    public void Save(string dsn, RecordLayout layout, IEnumerable<byte[]> records) => SequentialFile.Write(PathOf(dsn), records, append: false);
}

/// <summary>KSDS rows in PostgreSQL tables generated from the copybooks (db/migrations/001_create_vsam_tables.sql).</summary>
public sealed class PostgresPersistence(string connectionString, IReadOnlyDictionary<string, string> tables) : IKsdsPersistence
{
    public IEnumerable<byte[]> Load(string dsn, RecordLayout layout)
    {
        var fields = layout.Fields.Where(f => !f.IsFiller).ToList();
        using var conn = new NpgsqlConnection(connectionString);
        conn.Open();
        using var cmd = new NpgsqlCommand($"SELECT {string.Join(", ", fields.Select(Column))} FROM {tables[dsn]}", conn);
        using var reader = cmd.ExecuteReader();
        var rows = new List<byte[]>();
        while (reader.Read())
        {
            var rec = new FixedRecord(layout);
            for (var i = 0; i < fields.Count; i++)
            {
                if (fields[i].Kind == FieldKind.Text)
                {
                    rec.Set(fields[i].Name, reader.GetString(i));
                }
                else
                {
                    rec.Set(fields[i].Name, reader.GetDecimal(i));
                }
            }
            rows.Add(rec.Bytes);
        }
        return rows;
    }

    public void Save(string dsn, RecordLayout layout, IEnumerable<byte[]> records)
    {
        var fields = layout.Fields.Where(f => !f.IsFiller).ToList();
        using var conn = new NpgsqlConnection(connectionString);
        conn.Open();
        using var tx = conn.BeginTransaction();
        using (var del = new NpgsqlCommand($"DELETE FROM {tables[dsn]}", conn, tx))
        {
            del.ExecuteNonQuery();
        }
        var sql = $"INSERT INTO {tables[dsn]} ({string.Join(", ", fields.Select(Column))}) VALUES ({string.Join(", ", fields.Select((_, i) => "$" + (i + 1).ToString(CultureInfo.InvariantCulture)))})";
        foreach (var bytes in records)
        {
            var rec = new FixedRecord(layout, bytes);
            using var ins = new NpgsqlCommand(sql, conn, tx);
            foreach (var f in fields)
            {
                ins.Parameters.Add(new NpgsqlParameter { Value = f.Kind == FieldKind.Text ? rec.Text(f.Name) : rec.Number(f.Name) });
            }
            ins.ExecuteNonQuery();
        }
        tx.Commit();
    }

    private static string Column(FieldSpec f) => f.Name.ToLowerInvariant().Replace('-', '_');
}
