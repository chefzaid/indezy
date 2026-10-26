# ADR 0010: Keep Opportunity Files In PostgreSQL

- Status: Accepted
- Date: 2026-10-26

## Context

Freelancers attach files to their opportunities: the job description an ESN sent as PDF, the contract, the brief of a
technical test, a screenshot. They want to read a PDF or an image in the page rather than download it, and the files
belong to their workspace like the rest of its data: private, exported and deleted with it.

The platform gives applications a shared PostgreSQL database with backups and no object storage (its S3 targets hold
backups only). Indezy already keeps avatars in its database, as data URLs of at most 512 KB.

## Decision

- Store each file in the `project_attachments` table: its opportunity, name, type, size and content (`bytea` on
  PostgreSQL, through Hibernate's `VARBINARY` type so it is not a large object). Lists read everything but the content.
- Accept files of up to 10 MB, 20 per opportunity, of the types recognised from their first bytes (PDF, PNG, JPEG,
  GIF, WebP; Word and OpenDocument archives by their name; UTF-8 text): the type the browser declares is ignored.
- Serve the content only to the workspace that owns it, with the recognised type, `inline` for PDF and images and
  `attachment` otherwise, `X-Content-Type-Options: nosniff`, a sandboxing `Content-Security-Policy` and `no-store`
  caching. The interface reads it with the session's credentials and shows it from a local `blob:` address.

## Rationale

The database is already backed up, restored, secured by workspace checks and removed with the account, so the files
need nothing new: no bucket, credentials or second copy to keep consistent. At the sizes a freelancer keeps per
opportunity (a few documents of a few hundred kilobytes), PostgreSQL stores and serves them comfortably.

## Consequences

- The database grows with the files: 20 files of 10 MB per opportunity is the most one opportunity can hold.
- Reading a file loads it whole in the backend's memory, which the 10 MB cap bounds.
- Should the files outgrow the database, an object store can take their content behind the same API, the table
  keeping the metadata.
