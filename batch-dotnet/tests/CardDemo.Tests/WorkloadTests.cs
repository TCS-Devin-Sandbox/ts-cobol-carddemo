using System.Diagnostics;
using CardDemo.Batch.Programs;
using CardDemo.Orchestration;
using Xunit;

namespace CardDemo.Tests;

public class WorkloadTests
{
    private static readonly string Root = FindRoot();

    private static string FindRoot()
    {
        var d = new DirectoryInfo(AppContext.BaseDirectory);
        while (d is not null && !File.Exists(Path.Combine(d.FullName, "AGENTS.md")))
        {
            d = d.Parent;
        }
        return d?.FullName ?? throw new InvalidOperationException("repository root not found");
    }

    private static string Temp(string name) => Path.Combine(Path.GetTempPath(), "carddemo-tests", name + "-" + Guid.NewGuid().ToString("N")[..8]);

    public static TheoryData<string, string> Cases()
    {
        var data = new TheoryData<string, string>();
        foreach (var chain in new[] { "posting", "interest" })
        {
            foreach (var dir in Directory.EnumerateDirectories(Path.Combine(FindRoot(), "fixtures", chain)).Order(StringComparer.Ordinal))
            {
                data.Add(chain, Path.GetFileName(dir));
            }
        }
        return data;
    }

    /// <summary>Contract: every golden fixture passes the repository comparator (field level, SYSOUT, RC).</summary>
    [Theory]
    [MemberData(nameof(Cases))]
    public void Target_matches_legacy_golden_files(string chain, string fixture)
    {
        var outDir = Temp(fixture);
        ChainRunner.Run(chain, Path.Combine(Root, "fixtures", chain, fixture), outDir);
        var psi = new ProcessStartInfo("python3", ["tools/parity/compare.py", chain, fixture, "--actual", outDir, "--label", ".NET 8 target"])
        {
            WorkingDirectory = Root,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        using var p = Process.Start(psi)!;
        var output = p.StandardOutput.ReadToEnd() + p.StandardError.ReadToEnd();
        p.WaitForExit();
        Assert.True(p.ExitCode == 0, output);
    }

    /// <summary>Chunk checkpoint + step restart: a POSTTRAN failure after record 2, restarted, gives the clean-run outputs.</summary>
    [Fact]
    public void Posttran_restart_from_checkpoint_matches_clean_run()
    {
        var fixture = Path.Combine(Root, "fixtures", "posting", "p01-baseline-mixed");
        var clean = Temp("clean");
        var restarted = Temp("restart");
        ChainRunner.Run("posting", fixture, clean);

        var rc = ChainRunner.Run("posting", fixture, restarted, chunkSize: 1,
            programOverride: (job, step) => job == "POSTTRAN" ? new Cbtrn02c { FailAfter = 2 } : null);
        Assert.Equal(999, rc);
        Assert.True(File.Exists(Path.Combine(restarted, "checkpoints", "POSTTRAN.STEP15.checkpoint.json")));

        ChainRunner.Run("posting", fixture, restarted, restart: true, chunkSize: 1);
        foreach (var sub in new[] { "datasets", "sysout" })
        {
            foreach (var file in Directory.EnumerateFiles(Path.Combine(clean, sub)))
            {
                var other = Path.Combine(restarted, sub, Path.GetFileName(file));
                Assert.True(File.Exists(other), $"missing {sub}/{Path.GetFileName(file)} after restart");
                Assert.Equal(File.ReadAllBytes(file), File.ReadAllBytes(other));
            }
        }
        var ledger = File.ReadAllLines(Path.Combine(restarted, "ledger", "batch_run_ledger.jsonl"));
        Assert.Contains(ledger, l => l.Contains("\"Status\":\"ABEND\"", StringComparison.Ordinal));
    }

    [Fact]
    public void Readacct_and_readcard_run_clean()
    {
        foreach (var (chain, fixture) in new[] { ("readacct", "r01-sample"), ("readcard", "c01-sample") })
        {
            var outDir = Temp(chain);
            Assert.Equal(0, ChainRunner.Run(chain, Path.Combine(Root, "batch-dotnet", "fixtures", chain, fixture), outDir));
            Assert.NotEmpty(Directory.EnumerateFiles(Path.Combine(outDir, "sysout")));
        }
    }
}
