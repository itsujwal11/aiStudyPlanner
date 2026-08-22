# RAG Evaluation Table

Fill one row per real question asked against an uploaded PDF.
Run each question through **AI Chat** and record what the pipeline retrieved
(before reranking), what survived reranking, and whether the grounded answer was correct.

Retrieval ranks come from the Sources panel (`rank #n` = position after reranking;
the `retrievalRank` field in the API response shows the original vector position).

| # | Question | Expected source (page) | Top retrieved (page) | Top reranked (page) | Answer correct? | Notes |
|---|----------|------------------------|----------------------|---------------------|-----------------|-------|
| 1 | | | | | | |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |
| 6 | | | | | | |
| 7 | | | | | | |
| 8 | | | | | | |
| 9 | | | | | | |
| 10 | | | | | | |

## Metrics to report

- **Hit@5** — fraction of questions whose expected page appears among the 5 reranked sources.
- **MRR@5** — mean of `1/rank` of the first correct source after reranking.
- **Rerank lift** — how often reranking moved the correct source into the top 5
  compared with raw vector order (this demonstrates the reranker's contribution).
- **Answer accuracy** — fraction judged factually correct against the source pages.

## Procedure

1. Start backend + frontend; upload a course PDF (≥ 10 pages).
2. For each question: note the expected page (from reading the PDF),
   ask in AI Chat, then copy file/page/relevance/rank from the Sources panel.
3. Judge the answer strictly against the expected page content.
4. Fill the table, compute the metrics above.

## Automated retrieval checks

An opt-in integration test proves the pgvector layer works end-to-end
(non-zero cosine similarities, query-dependent ordering, strict per-user scoping):

```bash
# backend/, with Postgres running and `CREATE EXTENSION IF NOT EXISTS vector` applied
set RAG_INTEGRATION_TEST=true
mvn test -Dtest=RagRetrievalIntegrationTest
```

It is skipped by default so the regular `mvn test` suite stays green without a database.
