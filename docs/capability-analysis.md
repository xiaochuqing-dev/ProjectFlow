# Capability Analysis

Capability analysis consumes confirmed project sediments rather than raw development segments.

Each run records the input sediment count, pending count, and sediment IDs in the analysis job. Generated capability-card source references use `sediment:<uuid>`, while old segment references remain compatible.

Candidate replacement is atomic and never deletes confirmed cards. A failed run keeps the previous successful candidates and leaves all pending sediment states unchanged. After successful persistence, each input sediment records the analysis job and whether it formed at least one capability card.

The capability page shows the last successful time, last input count, sediments added or updated since that run, current pending count, current successful batch, latest unacknowledged failure, and history.
