using System.Text.Json;

namespace CardDemo.Batch.Restart;

/// <summary>Chunk-level restart point: input position plus program state committed with the data.</summary>
public sealed record Checkpoint(string Job, string Step, int Position, Dictionary<string, string> State, DateTime CommittedAt);

public interface ICheckpointStore
{
    Checkpoint? Load(string job, string step);
    void Save(Checkpoint checkpoint);
    void Clear(string job, string step);
}

public sealed class FileCheckpointStore(string dir) : ICheckpointStore
{
    private string PathOf(string job, string step) => Path.Combine(dir, $"{job}.{step}.checkpoint.json");

    public Checkpoint? Load(string job, string step) =>
        File.Exists(PathOf(job, step)) ? JsonSerializer.Deserialize<Checkpoint>(File.ReadAllText(PathOf(job, step))) : null;

    public void Save(Checkpoint checkpoint)
    {
        Directory.CreateDirectory(dir);
        var tmp = PathOf(checkpoint.Job, checkpoint.Step) + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(checkpoint));
        File.Move(tmp, PathOf(checkpoint.Job, checkpoint.Step), overwrite: true);
    }

    public void Clear(string job, string step)
    {
        if (File.Exists(PathOf(job, step)))
        {
            File.Delete(PathOf(job, step));
        }
    }
}
