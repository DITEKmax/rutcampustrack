# Diff evidence

Product change relative to the frozen target before hash
A6356840FAF4D34EF3B0E1022DC3CEACBD82AE2CEB92943FDD4198A578EA5AA9:

    @@ RequestDetail -> RequestPage
    +    /**
    +     * Canonical notification context resolved from one persisted request.
    +     * Transport events never supply group, student or private detail fields.
    +     */
    +    public record NotificationResolution(
    +            long groupId,
    +            long studentId,
    +            String studentName,
    +            RequestDetail detail
    +    ) {
    +    }

No line was removed or changed in the target model. The target's existing
ReasonOption(ExcuseType code, String label, boolean commentRequired) remains
byte-preserved.

Comparison against accepted P1 source returned git diff --no-index exit 1
with the sole hunk changing P1's two-field ReasonOption to the target's
three-field commentRequired form. This confirms the transplanted
NotificationResolution block is source-equivalent and the combined dependency
delta is minimal.