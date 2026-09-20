# H78 diagnosis before edit

## Request and reproduction

The H78 request was to close the complete Mongo descriptor boundary as one
batch. Root's actual I1 evidence recorded exit '1' with the strict assertion
that attachments[0].size must be a JSON integer. The reproduction is the
existing emitted Get-MongoSnapshot query followed by ConvertFrom-Json and
Assert-I1MongoDelta on the I1 snapshot.

## Source and runtime evidence

The native mongosh sample in
C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/h79-bson-sample.json
was run in pinned Mongo 7 with --nodb and no network (exit 0). It observed:

- NumberLong(10485760) has _bsontype Long, toString() 10485760,
  toNumber() 10485760, and JSON.stringify() shape
  {high:0,low:10485760,unsigned:false};
- NumberLong(9007199254740993) is unsafe and must not be emitted as a JSON
  number;
- IDs are strings, dates are UTC ISO values, attachment state is ACTIVE, and
  the persisted ticket status is lowercase submitted.

The trusted product source maps RequestAttachmentDocument.size and
RequestAttachmentDescriptorDocument.size to Mongo size as Java Long. The Java
service writes the byte-array length as a long; the outbox payload is JSON text
and therefore has JSON numeric sizes after JSON.parse, rather than native BSON
values. The status converter writes SUBMITTED as lowercase submitted. These
mappings make the persisted BSON Long expected behavior, not a product
persistence defect.

## Mismatch

Before this edit, Get-MongoSnapshot returned size:x.size for Mongo documents
and size:a.size for parsed outbox payloads. Native BSON Long values therefore
reached JSON.stringify as BSON wrapper objects, and ConvertFrom-Json produced a
JSON object where the strict PowerShell Assert-JsonInteger required an integer.
The outbox path needed an explicit JSON-number check as a separate boundary
because its payload is already JSON.

No code was changed before root supplied the native sample. No guessed BSON
representation is treated as runtime evidence.
