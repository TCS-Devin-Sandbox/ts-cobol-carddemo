using System.Text;
using CardDemo.Data;
using CardDemo.Domain;

namespace CardDemo.Orchestration;

/// <summary>Runs a chain on a fixture case and writes the parity contract: datasets/, sysout/, rc.json.</summary>
[Trace("tools/parity/record.py run_case")]
public static class ChainRunner
{
    public static readonly DateTime FrozenClock = new(2022, 7, 5, 10, 0, 0, DateTimeKind.Unspecified);

    public static int Run(string chain, string caseDir, string outDir, IKsdsPersistence? persistence = null, Action<string>? log = null,
        bool restart = false, Func<string, string, Batch.IBatchProgram?>? programOverride = null, int chunkSize = 1000)
    {
        var (setup, jobs) = JobCatalog.Chains[chain];
        if (!restart)
        {
            if (Directory.Exists(outDir))
            {
                Directory.Delete(outDir, recursive: true);
            }
            Directory.CreateDirectory(Path.Combine(outDir, "datasets"));
            foreach (var input in Directory.EnumerateFiles(Path.Combine(caseDir, "input"), "*.txt"))
            {
                File.Copy(input, Path.Combine(outDir, "datasets", Path.GetFileName(input)));
            }
        }
        var catalog = new DatasetCatalog(outDir, persistence);
        var runner = new JobRunner(catalog, FrozenClock, Guid.NewGuid().ToString("N")) { Log = log, Restart = restart, ProgramOverride = programOverride, ChunkSize = chunkSize };
        var rcs = new SortedDictionary<string, (int MaxRc, SortedDictionary<string, int> Steps)>(StringComparer.Ordinal);
        if (!restart)
        {
            foreach (var job in setup)
            {
                var rc = runner.Run(JobCatalog.Jobs[job]);
                if (rc > 8)
                {
                    throw new InvalidOperationException($"setup job {job} failed with RC {rc}");
                }
            }
        }
        var overall = 0;
        foreach (var job in jobs)
        {
            var rc = runner.Run(JobCatalog.Jobs[job]);
            var steps = new SortedDictionary<string, int>(StringComparer.Ordinal);
            foreach (var (k, v) in runner.StepReturnCodes.Where(kv => !kv.Key.Contains('.', StringComparison.Ordinal) || kv.Key.StartsWith(job, StringComparison.Ordinal)))
            {
                steps[k] = v;
            }
            rcs[job] = (rc, steps);
            runner.StepReturnCodes.Clear();
            overall = Math.Max(overall, rc);
            if (rc > 8)
            {
                break;
            }
        }
        File.WriteAllText(Path.Combine(outDir, "rc.json"), RcJson(rcs));
        return overall;
    }

    /// <summary>Same text as Python json.dumps(rcs, indent=1, sort_keys=True).</summary>
    public static string RcJson(SortedDictionary<string, (int MaxRc, SortedDictionary<string, int> Steps)> rcs)
    {
        var sb = new StringBuilder("{\n");
        var jobs = rcs.ToList();
        for (var j = 0; j < jobs.Count; j++)
        {
            sb.Append($" \"{jobs[j].Key}\": {{\n  \"maxrc\": {jobs[j].Value.MaxRc},\n  \"steps\": {{\n");
            var steps = jobs[j].Value.Steps.ToList();
            for (var s = 0; s < steps.Count; s++)
            {
                sb.Append($"   \"{steps[s].Key}\": {steps[s].Value}{(s < steps.Count - 1 ? "," : "")}\n");
            }
            sb.Append($"  }}\n }}{(j < jobs.Count - 1 ? "," : "")}\n");
        }
        return sb.Append('}').ToString();
    }
}
