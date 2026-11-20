using System.Globalization;
using System.Text.RegularExpressions;
using CardDemo.Domain;

namespace CardDemo.Data;

/// <summary>
/// The catalog: resolves DSNs (including GDG relative generations) to files under datasets/ and
/// hands out KSDS stores. KSDS persistence is pluggable (files or PostgreSQL).
/// </summary>
[Trace("z/OS catalog + GDG (DEFGDGB.jcl)")]
public sealed partial class DatasetCatalog(string workDir, IKsdsPersistence? ksdsPersistence = null)
{
    private readonly Dictionary<string, int> _jobGenerations = new(StringComparer.Ordinal);
    private readonly Dictionary<string, KeyedStore> _open = new(StringComparer.Ordinal);

    public static readonly IReadOnlyDictionary<string, (RecordLayout Layout, int KeyOffset, int KeyLength, (int, int)? Alt, string Table)> Clusters =
        new Dictionary<string, (RecordLayout, int, int, (int, int)?, string)>(StringComparer.Ordinal)
        {
            ["AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS"] = (Layouts.Account, 0, 11, null, "vsam_acctdata"),
            ["AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS"] = (Layouts.Card, 0, 16, null, "vsam_carddata"),
            ["AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS"] = (Layouts.CardXref, 0, 16, (25, 11), "vsam_cardxref"),
            ["AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS"] = (Layouts.TranCatBal, 0, 17, null, "vsam_tcatbalf"),
            ["AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS"] = (Layouts.DisclosureGroup, 0, 16, null, "vsam_discgrp"),
            ["AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS"] = (Layouts.TranType, 0, 2, null, "vsam_trantype"),
            ["AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS"] = (Layouts.TranCategory, 0, 6, null, "vsam_trancatg"),
            ["AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS"] = (Layouts.Transaction, 0, 16, null, "vsam_transact"),
        };

    public string WorkDir { get; } = workDir;
    public string DatasetDir => Path.Combine(WorkDir, "datasets");
    public IKsdsPersistence Persistence { get; } = ksdsPersistence ?? new FilePersistence(Path.Combine(workDir, "datasets"));

    /// <summary>A new job starts: (+n) references are re-evaluated against the catalog.</summary>
    public void BeginJob(IReadOnlyDictionary<string, int>? pinned = null)
    {
        _jobGenerations.Clear();
        foreach (var (k, v) in pinned ?? new Dictionary<string, int>())
        {
            _jobGenerations[k] = v;
        }
    }

    /// <summary>(+n) generations created by the current job - persisted so a restart writes the same generation.</summary>
    public IReadOnlyDictionary<string, int> JobGenerations => _jobGenerations;

    public string Resolve(string dsn)
    {
        var m = GdgRef().Match(dsn);
        if (!m.Success)
        {
            return dsn.EndsWith(".AIX.PATH", StringComparison.Ordinal) ? dsn[..^9] + ".KSDS" : dsn;
        }
        var baseName = m.Groups[1].Value;
        var rel = int.Parse(m.Groups[2].Value, CultureInfo.InvariantCulture);
        var current = Generations(baseName).DefaultIfEmpty(0).Max();
        int gen;
        if (rel > 0)
        {
            if (!_jobGenerations.TryGetValue(baseName, out var created))
            {
                created = current + rel;
                _jobGenerations[baseName] = created;
            }
            gen = created;
        }
        else
        {
            var before = _jobGenerations.TryGetValue(baseName, out var c) ? c - 1 : current;
            gen = before + rel;
        }
        return $"{baseName}.G{gen:0000}V00";
    }

    public string PathOf(string dsn) => Path.Combine(DatasetDir, Resolve(dsn) + ".txt");

    public bool IsCluster(string dsn) => Clusters.ContainsKey(Resolve(dsn));

    public KeyedStore Cluster(string dsn)
    {
        var name = Resolve(dsn);
        if (!_open.TryGetValue(name, out var store))
        {
            var c = Clusters[name];
            var persistence = Persistence is PostgresPersistence ? Persistence : new FilePersistence(DatasetDir);
            store = new KeyedStore(name, c.Layout, c.KeyOffset, c.KeyLength, persistence, c.Alt);
            _open[name] = store;
        }
        return store;
    }

    public List<byte[]> ReadSequential(string dsn, int lrecl) =>
        IsCluster(dsn) ? Cluster(dsn).Scan().Select(r => r.Bytes).ToList() : SequentialFile.Read(PathOf(dsn), lrecl);

    public void WriteSequential(string dsn, IEnumerable<byte[]> records, bool append = false) =>
        SequentialFile.Write(PathOf(dsn), records, append);

    /// <summary>Unload every cluster touched by the run to datasets/ (IDCAMS REPRO to a flat file) for comparison.</summary>
    public void UnloadClusters()
    {
        var files = new FilePersistence(DatasetDir);
        foreach (var s in _open.Values)
        {
            files.Save(s.Dsn, s.Layout, s.Scan().Select(r => r.Bytes));
        }
    }

    public void CommitAll()
    {
        foreach (var s in _open.Values)
        {
            s.Commit();
        }
    }

    private IEnumerable<int> Generations(string baseName)
    {
        if (!Directory.Exists(DatasetDir))
        {
            yield break;
        }
        foreach (var f in Directory.EnumerateFiles(DatasetDir, baseName + ".G*V00.txt"))
        {
            var g = Path.GetFileName(f)[(baseName.Length + 2)..(baseName.Length + 6)];
            if (int.TryParse(g, NumberStyles.None, CultureInfo.InvariantCulture, out var n))
            {
                yield return n;
            }
        }
    }

    [GeneratedRegex(@"^(.+)\(([+-]?\d+)\)$")]
    private static partial Regex GdgRef();
}
