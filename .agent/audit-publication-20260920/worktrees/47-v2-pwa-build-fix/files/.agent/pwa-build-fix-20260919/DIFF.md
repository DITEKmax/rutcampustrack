# Final product diff

```diff
diff --git a/frontends/mobile-core/src/features/requests/RequestCard.vue b/frontends/mobile-core/src/features/requests/RequestCard.vue
@@
   detail: RequestDetail
   offline: boolean
   cancelling?: boolean
-  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>>
+  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined
 }>(), {
   cancelling: false,
   attachmentStates: undefined,
 })

diff --git a/frontends/mobile-core/src/features/requests/RequestsScreen.vue b/frontends/mobile-core/src/features/requests/RequestsScreen.vue
@@
   cancellingId?: string | null
   hasNextPage?: boolean
   loadingMore?: boolean
-  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>>
+  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined
 }>(), {
   retrying: false,
   cancellingId: null,
   readOnly: false,
   hasNextPage: false,
   loadingMore: false,
   attachmentStates: undefined,
 })
```

`git diff --stat`: 2 files changed, 2 insertions, 2 deletions.
`git diff --check`: exit 0.
