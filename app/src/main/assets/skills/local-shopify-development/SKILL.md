---
name: local-shopify-development
description: Build Shopify apps, extensions and themes with the GraphQL Admin API, Shopify CLI, App Bridge, Polaris, webhooks, billing and Liquid.
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [shopify, graphql, ecommerce, liquid, app, theme, webhooks]
    related_skills: [local-frontend-design, seo]
---

# Shopify development

## Route the request first

- Route merchant tools, external service integrations and charged features to an app, and read `references/app-development.md` in this skill folder before building one.

- Route checkout, admin and point of sale customizations and discount logic to an extension, and read `references/extensions.md` in this skill folder before building one.

- Route storefront design and product or collection page changes to a theme, and read `references/themes.md` in this skill folder before editing one.

- Combine an app with a theme extension when a request needs both backend logic and storefront display.

## Use the Shopify CLI commands

- Install the development CLI with `npm install -g @shopify/cli@latest`, and keep it distinct from any merchant store connector, which is a separate service and not this CLI.

- Create and run an app with `shopify app init`, `shopify app dev` and `shopify app deploy`, and run `shopify app deploy` again to apply configuration changes from `shopify.app.toml`.

- Generate each extension with the CLI generator for its type, such as `shopify app generate extension --type checkout_ui_extension`, `--type admin_action`, `--type admin_block`, `--type pos_ui_extension` or `--type function`, instead of hand writing its scaffold.

- Develop themes with `shopify theme init`, `shopify theme dev`, `shopify theme pull --live` and `shopify theme push --development`, and pull the live theme before editing when the store already has customizations.

## Build apps and extensions

- Build embedded apps on Remix or React Router with App Bridge, and keep navigation, session handling and embedded checks inside that frame.

- Use the latest App Bridge through the AppProvider and the embedded auth strategy, because Built for Shopify status from July 2025 requires a current embedded App Bridge app.

- Use Polaris components for admin interfaces so the app matches the Shopify admin the merchant already knows.

- Declare only the access scopes the feature needs in `shopify.app.toml`, such as `read_products`, `write_products`, `read_orders` or `read_customers`, and name the scopes in the delivery report.

- Store API keys and secrets in environment variables, verify webhook HMAC with `authenticate.webhook()` or an equivalent check, validate the OAuth state value, and never hard code a key or secret.

- Use a persistent session storage such as PrismaSessionStorage instead of in memory storage, and authenticate admin routes with `authenticate.admin()` before reading shop data.

## Follow the GraphQL patterns

- Use the GraphQL Admin API as the default data path for all new code, because new public apps must use GraphQL from April 2025 and new features are GraphQL only, and keep REST calls only as legacy code to migrate.

- Paginate list results with cursors by requesting `pageInfo` with `hasNextPage` and `endCursor` and passing the cursor back as the `after` variable on the next query.

- Query only the fields the feature needs, check `userErrors` on every mutation such as `productCreate`, `productUpdate` and `metafieldsSet` before treating the result as saved, and read the response cost and throttle status from the GraphQL extensions when they are present.

- Use bulk operations such as `bulkOperationRunMutation` for datasets over 250 items instead of issuing one call per item.

- Handle rate limits with exponential backoff and retry after a 429 response, watching the GraphQL cost limit of 1000 points per 60 seconds and the REST limit of 2 requests per second per store when legacy REST code remains.

- Wire billing through Shopify billing when the app charges merchants, covering one time charges, recurring subscriptions and usage records, and test the full charge, approval and decline path with test charges before release.

## Handle webhooks within five seconds

- Return a 200 response within 5 seconds of a webhook arriving, because Shopify marks slower deliveries as failed, retries them up to 19 times over 48 hours and may cancel them after continued failures.

- Queue heavy webhook work for asynchronous processing with a job queue before responding, and keep only a quick operation under about one second inline before the response.

- Verify each webhook HMAC before trusting its payload, and make every handler safe to run twice because retries and duplicate deliveries happen.

## Apply the webhook and data sharp edges

- Request protected customer data access in the Partner Dashboard under App and API access before relying on customer names, emails, addresses or order customer fields, because that approval is separate from OAuth scopes and even `read_orders` returns empty or redacted fields without it.

- Handle missing protected data gracefully by checking whether customer fields are present before using them, and use the Customer Account API route when a logged in customer can supply their own data directly.

- Define webhooks in `shopify.app.toml` only and remove any webhook registration from the `afterAuth` hook in code, because defining the same topic in both places creates duplicate subscriptions, duplicate deliveries and registration conflicts.

- Check current subscriptions with a GraphQL `webhookSubscriptions` query when deliveries look duplicated, and deploy with `shopify app deploy` so the TOML definitions are the ones that register.

- Handle both the webhook path and the same path with a trailing slash, because Shopify can add a trailing slash and a strict framework or server will return a 404 for `/webhooks/` while `/webhooks` works, and test both forms before release.

- Implement all three mandatory GDPR handlers even when the app stores no customer data, covering `customers/data_request` by providing the stored customer data within 30 days, `customers/redact` by deleting that customer data and clearing customer fields on their orders, and `shop/redact` by deleting all sessions, customers, orders and settings for the shop after uninstall, and acknowledge each one with a 200 response.

- Clean up shop sessions and shop data on the `app/uninstalled` webhook as well as on `shop/redact`, so an uninstall does not leave live credentials behind.

## Build themes

- Write theme code in Liquid with sections and blocks the merchant can rearrange, and keep business logic out of templates.

- Keep product, price and image data coming from the store catalog, and never hard code catalog values into theme files.

## Troubleshoot from the known failures

- Treat rate limit errors by adding exponential backoff, switching large datasets to bulk operations and monitoring the shop API call limit header and the GraphQL cost extension.

- Treat authentication failures by checking that the access token is still valid, that every required scope was granted and that the OAuth flow completed.

- Treat a missing extension by checking that its target is correct, that it was published with `shopify app deploy` and that the app is installed on the test store.

- Treat missing webhook events by checking that the webhook URL is publicly accessible, that it answers with and without a trailing slash, that HMAC validation is correct and what the webhook logs in the Partner Dashboard show.

- Treat a failing GraphQL query by validating it against the schema in the GraphiQL explorer, checking the error for deprecated fields and confirming the required access scopes and protected data approval.

## Use the references and scripts in this folder

- Read `references/app-development.md` for the OAuth flow, GraphQL product and order operations, webhook handlers, billing mutations, metafields and rate limiting.

- Read `references/extensions.md` for checkout, admin, point of sale and customer account extension targets and components, and for Shopify Functions for discounts, payment customization, delivery customization and validation.

- Read `references/themes.md` for Liquid objects and filters, theme structure, sections and snippets, the theme workflow and common patterns such as pagination and cart AJAX.

- Run `python scripts/shopify_init.py` for interactive project scaffolding, and import the GraphQL helpers with `from shopify_graphql import ShopifyGraphQL` from `scripts/shopify_graphql.py` for query templates, pagination and rate limiting.

## Verify before delivery

- Test the finished work in a real development store, and check the exact merchant flow the request named, from first click to saved result.

- Report what was verified in the store, what was only code checked, and what still needs the merchant to confirm.

## Alternatives for better results

- Prefer a theme extension over a theme fork when the change is one storefront feature, so the merchant keeps theme updates.

- Prefer an existing Shopify function or extension point over a custom backend when Shopify already enforces the rule natively.
