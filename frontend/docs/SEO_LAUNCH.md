# SEO launch — static landing pages

This sprint added three statically-rendered Croatian landing pages plus the supporting SEO plumbing
(`sitemap.xml`, `robots.txt`, homepage metadata, and a `noindex` rule for shared plans). The pages are real HTML
documents — their title, content and metadata are in the source HTML, not injected by React — so a crawler sees
the full page without running any JavaScript.

## What was added

| URL | Source file |
| --- | --- |
| `https://budgetspaceai.com/hr/opremanje-prvog-stana/` | `frontend/public/hr/opremanje-prvog-stana/index.html` |
| `https://budgetspaceai.com/hr/dnevni-boravak-do-1000-eura/` | `frontend/public/hr/dnevni-boravak-do-1000-eura/index.html` |
| `https://budgetspaceai.com/hr/popis-stvari-za-useljenje/` | `frontend/public/hr/popis-stvari-za-useljenje/index.html` |

Shared styling: `frontend/public/seo.css`. Icon: the existing `frontend/public/favicon.svg`.

Everything under `frontend/public/` is copied verbatim into `dist/` by Vite at build time and served straight from
nginx. There is **no** React, router, or bundle involved in these pages.

## Where to edit content

- **Page copy / structure:** edit the relevant `frontend/public/hr/<slug>/index.html`. Plain HTML — no build step
  beyond `npm run build` (which just copies `public/` into `dist/`).
- **Look and feel:** edit `frontend/public/seo.css`. It is shared by all three pages and intentionally mirrors the
  app's warm-stone / navy / copper brand from `src/styles.css`, but is standalone (no dependency on the app CSS).
- **Homepage `<head>` metadata:** edit `frontend/index.html`.

### Constraints to keep

- No inline JavaScript on the SEO pages — the Content-Security-Policy (`script-src 'self'`) forbids it, and the
  pages don't need it.
- Each page keeps exactly **one** `<h1>` and its own unique `<title>`, meta description, canonical URL, and Open
  Graph / Twitter metadata.
- Don't add an `og:image` unless a real, public, stable share image exists — a fabricated one is worse than none.
- CTA links into the planner use the query-string preset read by `frontend/src/utils/landingPreset.ts`
  (`mode=single|move-in`, `room=<RoomType>`, `rooms=<comma-separated RoomType>`, `budget=<positive number>`),
  and end in `#planner` to scroll to the tool. Keep those params valid — invalid ones are safely ignored.

## How to add a new SEO page

1. Create `frontend/public/hr/<new-slug>/index.html`. Copy an existing page as a starting point and change the
   `<title>`, meta description, `<link rel="canonical">`, the Open Graph / Twitter tags, the `<h1>`, and the body.
2. Keep `<html lang="hr">`, link `/seo.css` and `/favicon.svg`, and reuse the existing CSS classes.
3. Point its CTAs at the planner with a valid preset (see the params above), ending in `#planner`.
4. Add internal links to and from the sibling pages (the `.related` block) so the set stays interlinked.
5. **Add the new URL to `frontend/public/sitemap.xml`.** A page missing from the sitemap is a page you're relying
   on Google to discover on its own.
6. `cd frontend && npm run build`, and confirm the file exists under `frontend/dist/hr/<new-slug>/index.html`.

## After deploy — Google Search Console

Indexing is a manual nudge, not part of the deploy. Once the pages are live on the production domain:

1. Open **Google Search Console** → <https://search.google.com/search-console>. Select (or add + verify) the
   `budgetspaceai.com` property. Verification is typically a DNS TXT record or an HTML meta tag.
2. **Submit the sitemap:** Search Console → *Sitemaps* → enter `sitemap.xml` (Search Console prepends the domain,
   so the full URL registered is `https://budgetspaceai.com/sitemap.xml`) → *Submit*.
3. **Request indexing per page:** use the *URL Inspection* tool at the top, paste each of the three new URLs one at
   a time, wait for the live check, then click *Request indexing*:
   - `https://budgetspaceai.com/hr/opremanje-prvog-stana/`
   - `https://budgetspaceai.com/hr/dnevni-boravak-do-1000-eura/`
   - `https://budgetspaceai.com/hr/popis-stvari-za-useljenje/`

## Realistic expectations

Submitting the sitemap and requesting indexing tells Google the pages *exist* and are ready to be crawled. It does
**not** guarantee they get indexed, and it says nothing about ranking. Indexing can take days to weeks, Google may
choose not to index a page, and ranking depends on many factors outside this repo. Treat these steps as "the pages
are technically prepared for indexing", nothing more.

## How shared plans stay out of the index

Shared-plan links (`/plan/<id>`) must keep working for anyone with the URL, but must not be indexed. Two layers:

- **Authoritative:** nginx returns `X-Robots-Tag: noindex, nofollow, noarchive` for any `/plan/<id>` request. It's
  driven by a `map` on `$request_uri` in `frontend/nginx.conf` (see the comments there — `$request_uri` survives
  the SPA `try_files` internal redirect, so the header lands on the real response). The homepage and SEO pages get
  an empty value, which nginx omits, so they stay indexable.
- **Defense-in-depth:** when the app opens a `/plan/:id` link it flips the document's `<meta name="robots">` to
  `noindex` (`frontend/src/App.tsx`), for a JS-rendering crawler.

`robots.txt` deliberately does **not** try to block `/plan/` — a `Disallow` there would only stop crawling, not
indexing, and can't carry `noindex`. The header is the right tool.
