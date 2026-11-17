# ADR-0003: Approval of three PIT equivalent mutants

## Status

Accepted — 2026-11-15

## Context

ADR-0002 sets the PIT kill score at 80% for the `core` module and counts an equivalent mutant as killed only after explicit human approval with a recorded reason. On 2026-11-15, `mutationCoverage` with `-DmutationThreshold=80` killed 357 of 360 mutants (99%). The tool still reports the other three as not killed. `docs/gauntlet-s3-pendencias.md` describes them and, until this ADR, stated that the note itself was not the approval.

The maintainer directed this record on 2026-11-15, on the evidence in that note and in `docs/gauntlet-s3-bloqueios.md`. The three mutants are in `StripeEventId.Cursor`. Each one leaves the observable result unchanged.

## Decision

The three mutants below are approved as equivalent. For the ADR-0002 kill-score rule they count as killed. The recorded tool score remains 357 of 360 (99%). That 99% meets the 80% threshold, and the equivalent-mutant clause is satisfied by this ADR. The PIT report is left as the tool wrote it. Production code and `pom.xml` stay unchanged. These mutants are not excluded in the build.

1. **`skipArray`.** The mutant replaces `if (!eat('[')) return false` with `return true`. PIT status: `NO_COVERAGE`. `skipValue` calls `skipArray` only when the next character is already `[`, so `eat('[')` succeeds and the `return false` path does not run. Replacing that return does not change which values are skipped.

2. **`decodeEscape`.** The mutant replaces `if (done()) return null` with `return ""`. PIT status: `SURVIVED`. The only caller is `readString`. At the end of the text the empty string is appended, the next loop sees a finished cursor, and `readString` returns null on both sides. The observable `id` does not change.

3. **`unicode`.** The mutant changes `index + 4 > text.length()` to `>=`. PIT status: `SURVIVED`. With exactly four hex digits there is no closing quote, so `readString` returns null on both sides. With five or more characters both comparisons are false. The decoded result does not change.

The dead `if (!eat('{')) return false` branch in `skipObject` was removed in an earlier change. It is not one of these three and is not covered by this approval. A later equivalent mutant needs its own recorded approval.

## Consequences

- Readers of the 99% PIT report can treat it as meeting ADR-0002: the percentage is above 80%, and the three residuals are the equivalents approved here.
- PIT keeps reporting `NO_COVERAGE` for `skipArray` and `SURVIVED` for `decodeEscape` and `unicode`. Nothing in the PIT configuration marks them killed.
- The 80% threshold, the `core`-only scope, and the command-line form `-DmutationThreshold=80` stay as ADR-0002 fixed them.
- `docs/gauntlet-s3-pendencias.md` remains the technical description and points here for the approval.

## Discarded

- Editing `StripeEventId` so these mutants disappear or become killable. That would change production code this record is not authorized to change.
- Excluding the mutants in the pitest plugin, or adding a threshold tag to `pom.xml`. The pom guard forbids those tags, and an exclusion would hide the mutants instead of approving them.
- Treating `docs/gauntlet-s3-pendencias.md` as the approval. ADR-0002 asks for a recorded human approval, which is this ADR.
- Rewriting the PIT HTML or XML report so the three lines say killed. The tool output stays the evidence; this ADR is the approval.
