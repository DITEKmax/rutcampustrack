# r2 evidence

## Frozen precondition

Before mutation, the target hash guard returned exit 0:

- target before: A6356840FAF4D34EF3B0E1022DC3CEACBD82AE2CEB92943FDD4198A578EA5AA9
- accepted P1 source: 142609485B22E52C9A6AA2506D332FD42E8F37CFEC178FCC22910777C9CB6D10
- target had zero NotificationResolution declarations
- target had ReasonOption(ExcuseType code, String label, boolean commentRequired)

## Applied correction

The exact accepted P1 block was inserted after RequestDetail:

    /**
     * Canonical notification context resolved from one persisted request.
     * Transport events never supply group, student or private detail fields.
     */
    public record NotificationResolution(
            long groupId,
            long studentId,
            String studentName,
            RequestDetail detail
    ) {
    }

No other product source was edited.

## Postcondition

The exact source-transform guard returned exit 0. It replaced the single P1
ReasonOption(ExcuseType code, String label) anchor in memory with the target
boolean form and found the result text-equivalent to the target. Derived
expected SHA and actual target SHA both equal
C5FF83BB1ABA886BA89DA94CD0A2832D7F88E566DBD37B3E2E820A2AD4C76EDD.

Static shape evidence: one declaration at line 149; fields at lines 150-153;
ReasonOption(..., boolean commentRequired) at line 169; no trailing whitespace.
Source SHA stayed frozen.

## Scope and ownership

The checkout was already pre-dirty with foreign work. The leaf changed only the
target file's missing model block; all r2 evidence is under the owned evidence
directory. No foreign source, generated contract, config, lockfile, test or
runtime state was taken over.