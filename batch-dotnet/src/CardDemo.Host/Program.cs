using CardDemo.Data;
using CardDemo.Orchestration;

// carddemo-batch run-chain <chain> <fixture-case-dir> <out-dir> [--postgres <conn>] [--restart] [--quiet]
if (args.Length < 4 || args[0] != "run-chain")
{
    Console.Error.WriteLine("usage: carddemo-batch run-chain <posting|interest> <fixture-case-dir> <out-dir> [--postgres <connection>] [--restart] [--quiet]");
    return 2;
}
var opts = args.Skip(4).ToList();
IKsdsPersistence? persistence = null;
var pg = opts.IndexOf("--postgres");
if (pg >= 0)
{
    persistence = new PostgresPersistence(opts[pg + 1], DatasetCatalog.Clusters.ToDictionary(kv => kv.Key, kv => kv.Value.Table));
}
var quiet = opts.Contains("--quiet");
var rc = ChainRunner.Run(args[1], args[2], args[3], persistence, quiet ? null : Console.WriteLine, restart: opts.Contains("--restart"));
if (!quiet)
{
    Console.WriteLine($"chain {args[1]} ended with max RC {rc}");
}
return rc > 8 ? 1 : 0;
