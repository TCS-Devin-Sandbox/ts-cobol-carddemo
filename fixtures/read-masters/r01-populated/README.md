# r01-populated
Reuses the posting baseline accounts and cross-references, with matching synthetic
cards and customers. All four jobs read two records in key order and return RC 0.
READACCT writes two packed-field records, two array records (including negative
values) and four variable records. The local COBDATFT stand-in supplies the
compact reissue date in PSCOMP; this is not a run of the HLASM load module.
