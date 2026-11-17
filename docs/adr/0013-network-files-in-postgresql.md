# ADR 0013: Keep The Network's Files In PostgreSQL Behind Signed Addresses

- Status: Accepted
- Date: 2026-11-16

## Context

Posts and messages of the member network carry images and documents. The owner chose to keep them in the database, as
opportunity attachments are ([ADR 0010](./0010-attachments-in-postgresql.md)), rather than in an object store (TODO.md,
Needs Input, 2026-11-16). Unlike an opportunity's files, a post's images show inline in feeds, many to a page, read by
other members, so the page cannot fetch each one with the session's token as the attachments are.

## Decision

- `network_files` holds each file's metadata (owner, kind, name, type, size, description, size in pixels, pages, the
  post, message or held post it belongs to, its place); `network_file_contents` holds its bytes and a PDF's first page
  drawn, read only to serve it.
- A file is uploaded on its own (`POST /api/network/files`) and attached when the post or the message goes out, the
  member's own and not attached yet; 4 images and 3 documents at most each; a post held for review keeps its files
  until it is published (they move to the post) or rejected (they go). Files never attached are dropped after a day.
- Images are decoded and written again (upright, 2048 pixels at most, JPEG or PNG), so no metadata of the original is
  kept; documents are PDF, OOXML or OpenDocument, refused with macros; a PDF's first page is drawn with PDFBox.
- Files are served through signed addresses under `/api/public/network-files` (HMAC-SHA256, valid to the end of the next
  hour), handed only with the posts and messages their reader may read, so that `<img>` shows them and the browser keeps
  them privately until they expire.

## Rationale

- One store, backed up and removed with the rest; the 50 MB cap (10 MB until 2026-11-17) and the resizing keep each row bounded.
- Signed addresses let the browser load images as images without exposing the token, and the access check stays where
  the post or the message is read.
- Addresses that change each hour keep the browser's copy useful while bounding how long a copied address works.

## Consequences

- The database grows with the images; at about 300 KB an image after resizing, 10,000 images are about 3 GB.
- A copied address works until it expires (two hours at most), for anyone who has it.
- Documents are scanned by the platform's ClamAV before they are kept (swirl-cloud runs it; Indezy only calls it), and
  refused while it cannot answer.
- Should the files outgrow the database, `network_file_contents` can move to an object store behind the same addresses.
