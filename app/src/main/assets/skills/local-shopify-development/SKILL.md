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

## Reference (inlined — the full detail)

The three reference files are inlined below, verbatim, because on this phone
only this SKILL.md is loaded — a pointer to references/ would be a dead end.
The same files also ship in this skill folder for other tools.

### App development (references/app-development.md)

<!-- 2026-11-20 (blanket audit): the text below is the cited reference file, inlined verbatim — on this phone only this SKILL.md is loaded, so a pointer to a sibling file would be a dead end. The same file also ships in this skill folder. -->

# App Development Reference

Guide for building Shopify apps with OAuth, GraphQL/REST APIs, webhooks, and billing.

## OAuth Authentication

### OAuth 2.0 Flow

**1. Redirect to Authorization URL:**

```
https://{shop}.myshopify.com/admin/oauth/authorize?
  client_id={api_key}&
  scope={scopes}&
  redirect_uri={redirect_uri}&
  state={nonce}
```

**2. Handle Callback:**

```javascript
app.get("/auth/callback", async (req, res) => {
  const { code, shop, state } = req.query;

  // Verify state to prevent CSRF
  if (state !== storedState) {
    return res.status(403).send("Invalid state");
  }

  // Exchange code for access token
  const accessToken = await exchangeCodeForToken(shop, code);

  // Store token securely
  await storeAccessToken(shop, accessToken);

  res.redirect(`https://${shop}/admin/apps/${appHandle}`);
});
```

**3. Exchange Code for Token:**

```javascript
async function exchangeCodeForToken(shop, code) {
  const response = await fetch(`https://${shop}/admin/oauth/access_token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      client_id: process.env.SHOPIFY_API_KEY,
      client_secret: process.env.SHOPIFY_API_SECRET,
      code,
    }),
  });

  const { access_token } = await response.json();
  return access_token;
}
```

### Access Scopes

**Common Scopes:**

- `read_products`, `write_products` - Product catalog
- `read_orders`, `write_orders` - Order management
- `read_customers`, `write_customers` - Customer data
- `read_inventory`, `write_inventory` - Stock levels
- `read_fulfillments`, `write_fulfillments` - Order fulfillment
- `read_shipping`, `write_shipping` - Shipping rates
- `read_analytics` - Store analytics
- `read_checkouts`, `write_checkouts` - Checkout data

Full list: https://shopify.dev/api/usage/access-scopes

### Session Tokens (Embedded Apps)

For embedded apps using App Bridge:

```javascript
import { getSessionToken } from '@shopify/app-bridge/utilities';

async function authenticatedFetch(url, options = {}) {
  const app = createApp({ ... });
  const token = await getSessionToken(app);

  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  });
}
```

## GraphQL Admin API

### Making Requests

```javascript
async function graphqlRequest(shop, accessToken, query, variables = {}) {
  const response = await fetch(
    `https://${shop}/admin/api/2026-01/graphql.json`,
    {
      method: "POST",
      headers: {
        "X-Shopify-Access-Token": accessToken,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ query, variables }),
    },
  );

  const data = await response.json();

  if (data.errors) {
    throw new Error(`GraphQL errors: ${JSON.stringify(data.errors)}`);
  }

  return data.data;
}
```

### Product Operations

**Create Product:**

```graphql
mutation CreateProduct($input: ProductInput!) {
  productCreate(input: $input) {
    product {
      id
      title
      handle
    }
    userErrors {
      field
      message
    }
  }
}
```

Variables:

```json
{
  "input": {
    "title": "New Product",
    "productType": "Apparel",
    "vendor": "Brand",
    "status": "ACTIVE",
    "variants": [
      { "price": "29.99", "sku": "SKU-001", "inventoryQuantity": 100 }
    ]
  }
}
```

**Update Product:**

```graphql
mutation UpdateProduct($input: ProductInput!) {
  productUpdate(input: $input) {
    product {
      id
      title
    }
    userErrors {
      field
      message
    }
  }
}
```

**Query Products:**

```graphql
query GetProducts($first: Int!, $query: String) {
  products(first: $first, query: $query) {
    edges {
      node {
        id
        title
        status
        variants(first: 5) {
          edges {
            node {
              id
              price
              inventoryQuantity
            }
          }
        }
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
```

### Order Operations

**Query Orders:**

```graphql
query GetOrders($first: Int!) {
  orders(first: $first) {
    edges {
      node {
        id
        name
        createdAt
        displayFinancialStatus
        totalPriceSet {
          shopMoney {
            amount
            currencyCode
          }
        }
        customer {
          email
          firstName
          lastName
        }
      }
    }
  }
}
```

**Fulfill Order:**

```graphql
mutation FulfillOrder($fulfillment: FulfillmentInput!) {
  fulfillmentCreate(fulfillment: $fulfillment) {
    fulfillment {
      id
      status
      trackingInfo {
        number
        url
      }
    }
    userErrors {
      field
      message
    }
  }
}
```

## Webhooks

### Configuration

In `shopify.app.toml`:

```toml
[webhooks]
api_version = "2025-01"

[[webhooks.subscriptions]]
topics = ["orders/create"]
uri = "/webhooks/orders/create"

[[webhooks.subscriptions]]
topics = ["products/update"]
uri = "/webhooks/products/update"

[[webhooks.subscriptions]]
topics = ["app/uninstalled"]
uri = "/webhooks/app/uninstalled"

# GDPR mandatory webhooks
[webhooks.privacy_compliance]
customer_data_request_url = "/webhooks/gdpr/data-request"
customer_deletion_url = "/webhooks/gdpr/customer-deletion"
shop_deletion_url = "/webhooks/gdpr/shop-deletion"
```

### Webhook Handler

```javascript
import crypto from "crypto";

function verifyWebhook(req) {
  const hmac = req.headers["x-shopify-hmac-sha256"];
  const body = req.rawBody; // Raw body buffer

  const hash = crypto
    .createHmac("sha256", process.env.SHOPIFY_API_SECRET)
    .update(body, "utf8")
    .digest("base64");

  return hmac === hash;
}

app.post("/webhooks/orders/create", async (req, res) => {
  if (!verifyWebhook(req)) {
    return res.status(401).send("Unauthorized");
  }

  const order = req.body;
  console.log("New order:", order.id, order.name);

  // Process order...

  res.status(200).send("OK");
});
```

### Common Webhook Topics

**Orders:**

- `orders/create`, `orders/updated`, `orders/delete`
- `orders/paid`, `orders/cancelled`, `orders/fulfilled`

**Products:**

- `products/create`, `products/update`, `products/delete`

**Customers:**

- `customers/create`, `customers/update`, `customers/delete`

**Inventory:**

- `inventory_levels/update`

**App:**

- `app/uninstalled` (critical for cleanup)

## Billing Integration

### App Charges

**One-time Charge:**

```graphql
mutation CreateCharge($input: AppPurchaseOneTimeInput!) {
  appPurchaseOneTimeCreate(input: $input) {
    appPurchaseOneTime {
      id
      name
      price {
        amount
      }
      status
      confirmationUrl
    }
    userErrors {
      field
      message
    }
  }
}
```

Variables:

```json
{
  "input": {
    "name": "Premium Feature",
    "price": { "amount": 49.99, "currencyCode": "USD" },
    "returnUrl": "https://your-app.com/billing/callback"
  }
}
```

**Recurring Charge (Subscription):**

```graphql
mutation CreateSubscription(
  $name: String!
  $returnUrl: URL!
  $lineItems: [AppSubscriptionLineItemInput!]!
  $trialDays: Int
) {
  appSubscriptionCreate(
    name: $name
    returnUrl: $returnUrl
    lineItems: $lineItems
    trialDays: $trialDays
  ) {
    appSubscription {
      id
      name
      status
    }
    confirmationUrl
    userErrors {
      field
      message
    }
  }
}
```

Variables:

```json
{
  "name": "Monthly Subscription",
  "returnUrl": "https://your-app.com/billing/callback",
  "trialDays": 7,
  "lineItems": [
    {
      "plan": {
        "appRecurringPricingDetails": {
          "price": { "amount": 29.99, "currencyCode": "USD" },
          "interval": "EVERY_30_DAYS"
        }
      }
    }
  ]
}
```

**Usage-based Billing:**

```graphql
mutation CreateUsageCharge(
  $subscriptionLineItemId: ID!
  $price: MoneyInput!
  $description: String!
) {
  appUsageRecordCreate(
    subscriptionLineItemId: $subscriptionLineItemId
    price: $price
    description: $description
  ) {
    appUsageRecord {
      id
      price {
        amount
        currencyCode
      }
      description
    }
    userErrors {
      field
      message
    }
  }
}
```

Variables:

```json
{
  "subscriptionLineItemId": "gid://shopify/AppSubscriptionLineItem/123",
  "price": { "amount": "5.00", "currencyCode": "USD" },
  "description": "100 API calls used"
}
```

## Metafields

### Create/Update Metafields

```graphql
mutation SetMetafields($metafields: [MetafieldsSetInput!]!) {
  metafieldsSet(metafields: $metafields) {
    metafields {
      id
      namespace
      key
      value
    }
    userErrors {
      field
      message
    }
  }
}
```

Variables:

```json
{
  "metafields": [
    {
      "ownerId": "gid://shopify/Product/123",
      "namespace": "custom",
      "key": "instructions",
      "value": "Handle with care",
      "type": "single_line_text_field"
    }
  ]
}
```

**Metafield Types:**

- `single_line_text_field`, `multi_line_text_field`
- `number_integer`, `number_decimal`
- `date`, `date_time`
- `url`, `json`
- `file_reference`, `product_reference`

## Rate Limiting

### GraphQL Cost-Based Limits

**Limits:**

- Available points: 2000
- Restore rate: 100 points/second
- Max query cost: 2000

**Check Cost:**

```javascript
const response = await graphqlRequest(shop, token, query);
const cost = response.extensions?.cost;

console.log(
  `Cost: ${cost.actualQueryCost}/${cost.throttleStatus.maximumAvailable}`,
);
```

**Handle Throttling:**

```javascript
async function graphqlWithRetry(shop, token, query, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      return await graphqlRequest(shop, token, query);
    } catch (error) {
      if (error.message.includes("Throttled") && i < retries - 1) {
        await sleep(Math.pow(2, i) * 1000); // Exponential backoff
        continue;
      }
      throw error;
    }
  }
}
```

## Best Practices

**Security:**

- Store credentials in environment variables
- Verify webhook HMAC signatures
- Validate OAuth state parameter
- Use HTTPS for all endpoints
- Implement rate limiting on your endpoints

**Performance:**

- Cache access tokens securely
- Use bulk operations for large datasets
- Implement pagination for queries
- Monitor GraphQL query costs

**Reliability:**

- Implement exponential backoff for retries
- Handle webhook delivery failures
- Log errors for debugging
- Monitor app health metrics

**Compliance:**

- Implement GDPR webhooks (mandatory)
- Handle customer data deletion requests
- Provide data export functionality
- Follow data retention policies

### Extensions (references/extensions.md)

# Extensions Reference

Guide for building UI extensions and Shopify Functions.

## Checkout UI Extensions

Customize checkout and thank-you pages with native-rendered components.

### Extension Points

**Block Targets (Merchant-Configurable):**

- `purchase.checkout.block.render` - Main checkout
- `purchase.thank-you.block.render` - Thank you page

**Static Targets (Fixed Position):**

- `purchase.checkout.header.render-after`
- `purchase.checkout.contact.render-before`
- `purchase.checkout.shipping-option-list.render-after`
- `purchase.checkout.payment-method-list.render-after`
- `purchase.checkout.footer.render-before`

### Setup

```bash
shopify app generate extension --type checkout_ui_extension
```

Configuration (`shopify.extension.toml`):

```toml
api_version = "2026-01"
name = "gift-message"
type = "ui_extension"

[[extensions.targeting]]
target = "purchase.checkout.block.render"

[capabilities]
network_access = true
api_access = true
```

### Basic Example

```javascript
import {
  reactExtension,
  BlockStack,
  TextField,
  Checkbox,
  useApi,
} from "@shopify/ui-extensions-react/checkout";

export default reactExtension("purchase.checkout.block.render", () => (
  <Extension />
));

function Extension() {
  const [message, setMessage] = useState("");
  const [isGift, setIsGift] = useState(false);
  const { applyAttributeChange } = useApi();

  useEffect(() => {
    if (isGift) {
      applyAttributeChange({
        type: "updateAttribute",
        key: "gift_message",
        value: message,
      });
    }
  }, [message, isGift]);

  return (
    <BlockStack spacing="loose">
      <Checkbox checked={isGift} onChange={setIsGift}>
        This is a gift
      </Checkbox>
      {isGift && (
        <TextField
          label="Gift Message"
          value={message}
          onChange={setMessage}
          multiline={3}
        />
      )}
    </BlockStack>
  );
}
```

### Common Hooks

**useApi:**

```javascript
const { extensionPoint, shop, storefront, i18n, sessionToken } = useApi();
```

**useCartLines:**

```javascript
const lines = useCartLines();
lines.forEach((line) => {
  console.log(line.merchandise.product.title, line.quantity);
});
```

**useShippingAddress:**

```javascript
const address = useShippingAddress();
console.log(address.city, address.countryCode);
```

**useApplyCartLinesChange:**

```javascript
const applyChange = useApplyCartLinesChange();

async function addItem() {
  await applyChange({
    type: "addCartLine",
    merchandiseId: "gid://shopify/ProductVariant/123",
    quantity: 1,
  });
}
```

### Core Components

**Layout:**

- `BlockStack` - Vertical stacking
- `InlineStack` - Horizontal layout
- `Grid`, `GridItem` - Grid layout
- `View` - Container
- `Divider` - Separator

**Input:**

- `TextField` - Text input
- `Checkbox` - Boolean
- `Select` - Dropdown
- `DatePicker` - Date selection
- `Form` - Form wrapper

**Display:**

- `Text`, `Heading` - Typography
- `Banner` - Messages
- `Badge` - Status
- `Image` - Images
- `Link` - Hyperlinks
- `List`, `ListItem` - Lists

**Interactive:**

- `Button` - Actions
- `Modal` - Overlays
- `Pressable` - Click areas

## Admin UI Extensions

Extend Shopify admin interface.

### Admin Action

Custom actions on resource pages.

```bash
shopify app generate extension --type admin_action
```

```javascript
import {
  reactExtension,
  AdminAction,
  Button,
} from "@shopify/ui-extensions-react/admin";

export default reactExtension("admin.product-details.action.render", () => (
  <Extension />
));

function Extension() {
  const { data } = useData();

  async function handleExport() {
    const response = await fetch("/api/export", {
      method: "POST",
      body: JSON.stringify({ productId: data.product.id }),
    });
    console.log("Exported:", await response.json());
  }

  return (
    <AdminAction
      title="Export Product"
      primaryAction={<Button onPress={handleExport}>Export</Button>}
    />
  );
}
```

**Targets:**

- `admin.product-details.action.render`
- `admin.order-details.action.render`
- `admin.customer-details.action.render`

### Admin Block

Embedded content in admin pages.

```javascript
import {
  reactExtension,
  BlockStack,
  Text,
  Badge,
} from "@shopify/ui-extensions-react/admin";

export default reactExtension("admin.product-details.block.render", () => (
  <Extension />
));

function Extension() {
  const { data } = useData();
  const [analytics, setAnalytics] = useState(null);

  useEffect(() => {
    fetchAnalytics(data.product.id).then(setAnalytics);
  }, []);

  return (
    <BlockStack>
      <Text variant="headingMd">Product Analytics</Text>
      <Text>Views: {analytics?.views || 0}</Text>
      <Text>Conversions: {analytics?.conversions || 0}</Text>
      <Badge tone={analytics?.trending ? "success" : "info"}>
        {analytics?.trending ? "Trending" : "Normal"}
      </Badge>
    </BlockStack>
  );
}
```

**Targets:**

- `admin.product-details.block.render`
- `admin.order-details.block.render`
- `admin.customer-details.block.render`

## POS UI Extensions

Customize Point of Sale experience.

### Smart Grid Tile

Quick access action on POS home screen.

```javascript
import {
  reactExtension,
  SmartGridTile,
} from "@shopify/ui-extensions-react/pos";

export default reactExtension("pos.home.tile.render", () => <Extension />);

function Extension() {
  function handlePress() {
    // Navigate to custom workflow
  }

  return (
    <SmartGridTile
      title="Gift Cards"
      subtitle="Manage gift cards"
      onPress={handlePress}
    />
  );
}
```

### POS Modal

Full-screen workflow.

```javascript
import {
  reactExtension,
  Screen,
  BlockStack,
  Button,
  TextField,
} from "@shopify/ui-extensions-react/pos";

export default reactExtension("pos.home.modal.render", () => <Extension />);

function Extension() {
  const { navigation } = useApi();
  const [amount, setAmount] = useState("");

  function handleIssue() {
    // Issue gift card
    navigation.pop();
  }

  return (
    <Screen name="Gift Card" title="Issue Gift Card">
      <BlockStack>
        <TextField label="Amount" value={amount} onChange={setAmount} />
        <TextField label="Recipient Email" />
        <Button onPress={handleIssue}>Issue</Button>
      </BlockStack>
    </Screen>
  );
}
```

## Customer Account Extensions

Customize customer account pages.

### Order Status Extension

```javascript
import {
  reactExtension,
  BlockStack,
  Text,
  Button,
} from "@shopify/ui-extensions-react/customer-account";

export default reactExtension(
  "customer-account.order-status.block.render",
  () => <Extension />,
);

function Extension() {
  const { order } = useApi();

  function handleReturn() {
    // Initiate return
  }

  return (
    <BlockStack>
      <Text variant="headingMd">Need to return?</Text>
      <Text>Start return for order {order.name}</Text>
      <Button onPress={handleReturn}>Start Return</Button>
    </BlockStack>
  );
}
```

**Targets:**

- `customer-account.order-status.block.render`
- `customer-account.order-index.block.render`
- `customer-account.profile.block.render`

## Shopify Functions

Serverless backend customization.

### Function Types

**Discounts:**

- `order_discount` - Order-level discounts
- `product_discount` - Product-specific discounts
- `shipping_discount` - Shipping discounts

**Payment Customization:**

- Hide/rename/reorder payment methods

**Delivery Customization:**

- Custom shipping options
- Delivery rules

**Validation:**

- Cart validation rules
- Checkout validation

### Create Function

```bash
shopify app generate extension --type function
```

### Order Discount Function

```javascript
// input.graphql
query Input {
  cart {
    lines {
      quantity
      merchandise {
        ... on ProductVariant {
          product {
            hasTag(tag: "bulk-discount")
          }
        }
      }
    }
  }
}

// function.js
export default function orderDiscount(input) {
  const targets = input.cart.lines
    .filter(line => line.merchandise.product.hasTag)
    .map(line => ({
      productVariant: { id: line.merchandise.id }
    }));

  if (targets.length === 0) {
    return { discounts: [] };
  }

  return {
    discounts: [{
      targets,
      value: {
        percentage: {
          value: 10  // 10% discount
        }
      }
    }]
  };
}
```

### Payment Customization Function

```javascript
export default function paymentCustomization(input) {
  const hidePaymentMethods = input.cart.lines.some(
    (line) => line.merchandise.product.hasTag,
  );

  if (!hidePaymentMethods) {
    return { operations: [] };
  }

  return {
    operations: [
      {
        hide: {
          paymentMethodId: "gid://shopify/PaymentMethod/123",
        },
      },
    ],
  };
}
```

### Validation Function

```javascript
export default function cartValidation(input) {
  const errors = [];

  // Max 5 items per cart
  if (input.cart.lines.length > 5) {
    errors.push({
      localizedMessage: "Maximum 5 items allowed per order",
      target: "cart",
    });
  }

  // Min $50 for wholesale
  const isWholesale = input.cart.lines.some(
    (line) => line.merchandise.product.hasTag,
  );

  if (isWholesale && input.cart.cost.totalAmount.amount < 50) {
    errors.push({
      localizedMessage: "Wholesale orders require $50 minimum",
      target: "cart",
    });
  }

  return { errors };
}
```

## Network Requests

Extensions can call external APIs.

```javascript
import { useApi } from "@shopify/ui-extensions-react/checkout";

function Extension() {
  const { sessionToken } = useApi();

  async function fetchData() {
    const token = await sessionToken.get();

    const response = await fetch("https://your-app.com/api/data", {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });

    return await response.json();
  }
}
```

## Best Practices

**Performance:**

- Lazy load data
- Memoize expensive computations
- Use loading states
- Minimize re-renders

**UX:**

- Provide clear error messages
- Show loading indicators
- Validate inputs
- Support keyboard navigation

**Security:**

- Verify session tokens on backend
- Sanitize user input
- Use HTTPS for all requests
- Don't expose sensitive data

**Testing:**

- Test on development stores
- Verify mobile/desktop
- Check accessibility
- Test edge cases

## Resources

- Checkout Extensions: https://shopify.dev/docs/api/checkout-extensions
- Admin Extensions: https://shopify.dev/docs/apps/admin/extensions
- Functions: https://shopify.dev/docs/apps/functions
- Components: https://shopify.dev/docs/api/checkout-ui-extensions/components

### Themes (references/themes.md)

# Themes Reference

Guide for developing Shopify themes with Liquid templating.

## Liquid Templating

### Syntax Basics

**Objects (Output):**
```liquid
{{ product.title }}
{{ product.price | money }}
{{ customer.email }}
```

**Tags (Logic):**
```liquid
{% if product.available %}
  <button>Add to Cart</button>
{% else %}
  <p>Sold Out</p>
{% endif %}

{% for product in collection.products %}
  {{ product.title }}
{% endfor %}

{% case product.type %}
  {% when 'Clothing' %}
    <span>Apparel</span>
  {% when 'Shoes' %}
    <span>Footwear</span>
  {% else %}
    <span>Other</span>
{% endcase %}
```

**Filters (Transform):**
```liquid
{{ product.title | upcase }}
{{ product.price | money }}
{{ product.description | strip_html | truncate: 100 }}
{{ product.image | img_url: 'medium' }}
{{ 'now' | date: '%B %d, %Y' }}
```

### Common Objects

**Product:**
```liquid
{{ product.id }}
{{ product.title }}
{{ product.handle }}
{{ product.description }}
{{ product.price }}
{{ product.compare_at_price }}
{{ product.available }}
{{ product.type }}
{{ product.vendor }}
{{ product.tags }}
{{ product.images }}
{{ product.variants }}
{{ product.featured_image }}
{{ product.url }}
```

**Collection:**
```liquid
{{ collection.title }}
{{ collection.handle }}
{{ collection.description }}
{{ collection.products }}
{{ collection.products_count }}
{{ collection.image }}
{{ collection.url }}
```

**Cart:**
```liquid
{{ cart.item_count }}
{{ cart.total_price }}
{{ cart.items }}
{{ cart.note }}
{{ cart.attributes }}
```

**Customer:**
```liquid
{{ customer.email }}
{{ customer.first_name }}
{{ customer.last_name }}
{{ customer.orders_count }}
{{ customer.total_spent }}
{{ customer.addresses }}
{{ customer.default_address }}
```

**Shop:**
```liquid
{{ shop.name }}
{{ shop.email }}
{{ shop.domain }}
{{ shop.currency }}
{{ shop.money_format }}
{{ shop.enabled_payment_types }}
```

### Common Filters

**String:**
- `upcase`, `downcase`, `capitalize`
- `strip_html`, `strip_newlines`
- `truncate: 100`, `truncatewords: 20`
- `replace: 'old', 'new'`

**Number:**
- `money` - Format currency
- `round`, `ceil`, `floor`
- `times`, `divided_by`, `plus`, `minus`

**Array:**
- `join: ', '`
- `first`, `last`
- `size`
- `map: 'property'`
- `where: 'property', 'value'`

**URL:**
- `img_url: 'size'` - Image URL
- `url_for_type`, `url_for_vendor`
- `link_to`, `link_to_type`

**Date:**
- `date: '%B %d, %Y'`

## Theme Architecture

### Directory Structure

```
theme/
├── assets/              # CSS, JS, images
├── config/              # Theme settings
│   ├── settings_schema.json
│   └── settings_data.json
├── layout/              # Base templates
│   └── theme.liquid
├── locales/             # Translations
│   └── en.default.json
├── sections/            # Reusable blocks
│   ├── header.liquid
│   ├── footer.liquid
│   └── product-grid.liquid
├── snippets/            # Small components
│   ├── product-card.liquid
│   └── icon.liquid
└── templates/           # Page templates
    ├── index.json
    ├── product.json
    ├── collection.json
    └── cart.liquid
```

### Layout

Base template wrapping all pages (`layout/theme.liquid`):

```liquid
<!DOCTYPE html>
<html lang="{{ request.locale.iso_code }}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>{{ page_title }}</title>

  {{ content_for_header }}

  <link rel="stylesheet" href="{{ 'theme.css' | asset_url }}">
</head>
<body>
  {% section 'header' %}

  <main>
    {{ content_for_layout }}
  </main>

  {% section 'footer' %}

  <script src="{{ 'theme.js' | asset_url }}"></script>
</body>
</html>
```

### Templates

Page-specific structures (`templates/product.json`):

```json
{
  "sections": {
    "main": {
      "type": "product-template",
      "settings": {
        "show_vendor": true,
        "show_quantity_selector": true
      }
    },
    "recommendations": {
      "type": "product-recommendations"
    }
  },
  "order": ["main", "recommendations"]
}
```

Legacy format (`templates/product.liquid`):
```liquid
<div class="product">
  <div class="product-images">
    <img src="{{ product.featured_image | img_url: 'large' }}" alt="{{ product.title }}">
  </div>

  <div class="product-details">
    <h1>{{ product.title }}</h1>
    <p class="price">{{ product.price | money }}</p>

    {% form 'product', product %}
      <select name="id">
        {% for variant in product.variants %}
          <option value="{{ variant.id }}">{{ variant.title }} - {{ variant.price | money }}</option>
        {% endfor %}
      </select>

      <button type="submit">Add to Cart</button>
    {% endform %}
  </div>
</div>
```

### Sections

Reusable content blocks (`sections/product-grid.liquid`):

```liquid
<div class="product-grid">
  {% for product in section.settings.collection.products %}
    <div class="product-card">
      <a href="{{ product.url }}">
        <img src="{{ product.featured_image | img_url: 'medium' }}" alt="{{ product.title }}">
        <h3>{{ product.title }}</h3>
        <p>{{ product.price | money }}</p>
      </a>
    </div>
  {% endfor %}
</div>

{% schema %}
{
  "name": "Product Grid",
  "settings": [
    {
      "type": "collection",
      "id": "collection",
      "label": "Collection"
    },
    {
      "type": "range",
      "id": "products_per_row",
      "min": 2,
      "max": 5,
      "step": 1,
      "default": 4,
      "label": "Products per row"
    }
  ],
  "presets": [
    {
      "name": "Product Grid"
    }
  ]
}
{% endschema %}
```

### Snippets

Small reusable components (`snippets/product-card.liquid`):

```liquid
<div class="product-card">
  <a href="{{ product.url }}">
    {% if product.featured_image %}
      <img src="{{ product.featured_image | img_url: 'medium' }}" alt="{{ product.title }}">
    {% endif %}
    <h3>{{ product.title }}</h3>
    <p class="price">{{ product.price | money }}</p>
    {% if product.compare_at_price > product.price %}
      <p class="sale-price">{{ product.compare_at_price | money }}</p>
    {% endif %}
  </a>
</div>
```

Include snippet:
```liquid
{% render 'product-card', product: product %}
```

## Development Workflow

### Setup

```bash
# Initialize new theme
shopify theme init

# Choose Dawn (reference theme) or blank
```

### Local Development

```bash
# Start local server
shopify theme dev

# Preview at http://localhost:9292
# Changes auto-sync to development theme
```

### Pull Theme

```bash
# Pull live theme
shopify theme pull --live

# Pull specific theme
shopify theme pull --theme=123456789

# Pull only templates
shopify theme pull --only=templates
```

### Push Theme

```bash
# Push to development theme
shopify theme push --development

# Create new unpublished theme
shopify theme push --unpublished

# Push specific files
shopify theme push --only=sections,snippets
```

### Theme Check

Lint theme code:
```bash
shopify theme check
shopify theme check --auto-correct
```

## Common Patterns

### Product Form with Variants

```liquid
{% form 'product', product %}
  {% unless product.has_only_default_variant %}
    {% for option in product.options_with_values %}
      <div class="product-option">
        <label>{{ option.name }}</label>
        <select name="options[{{ option.name }}]">
          {% for value in option.values %}
            <option value="{{ value }}">{{ value }}</option>
          {% endfor %}
        </select>
      </div>
    {% endfor %}
  {% endunless %}

  <input type="hidden" name="id" value="{{ product.selected_or_first_available_variant.id }}">
  <input type="number" name="quantity" value="1" min="1">

  <button type="submit" {% unless product.available %}disabled{% endunless %}>
    {% if product.available %}Add to Cart{% else %}Sold Out{% endif %}
  </button>
{% endform %}
```

### Pagination

```liquid
{% paginate collection.products by 12 %}
  {% for product in collection.products %}
    {% render 'product-card', product: product %}
  {% endfor %}

  {% if paginate.pages > 1 %}
    <div class="pagination">
      {% if paginate.previous %}
        <a href="{{ paginate.previous.url }}">Previous</a>
      {% endif %}

      {% for part in paginate.parts %}
        {% if part.is_link %}
          <a href="{{ part.url }}">{{ part.title }}</a>
        {% else %}
          <span class="current">{{ part.title }}</span>
        {% endif %}
      {% endfor %}

      {% if paginate.next %}
        <a href="{{ paginate.next.url }}">Next</a>
      {% endif %}
    </div>
  {% endif %}
{% endpaginate %}
```

### Cart AJAX

```javascript
// Add to cart
fetch('/cart/add.js', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    id: variantId,
    quantity: 1
  })
})
.then(res => res.json())
.then(item => console.log('Added:', item));

// Get cart
fetch('/cart.js')
  .then(res => res.json())
  .then(cart => console.log('Cart:', cart));

// Update cart
fetch('/cart/change.js', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    id: lineItemKey,
    quantity: 2
  })
})
.then(res => res.json());
```

## Metafields in Themes

Access custom data:

```liquid
{{ product.metafields.custom.care_instructions }}
{{ product.metafields.custom.material.value }}

{% if product.metafields.custom.featured %}
  <span class="badge">Featured</span>
{% endif %}
```

## Best Practices

**Performance:**
- Optimize images (use appropriate sizes)
- Minimize Liquid logic complexity
- Use lazy loading for images
- Defer non-critical JavaScript

**Accessibility:**
- Use semantic HTML
- Include alt text for images
- Support keyboard navigation
- Ensure sufficient color contrast

**SEO:**
- Use descriptive page titles
- Include meta descriptions
- Structure content with headings
- Implement schema markup

**Code Quality:**
- Follow Shopify theme guidelines
- Use consistent naming conventions
- Comment complex logic
- Keep sections focused and reusable

## Resources

- Theme Development: https://shopify.dev/docs/themes
- Liquid Reference: https://shopify.dev/docs/api/liquid
- Dawn Theme: https://github.com/Shopify/dawn
- Theme Check: https://shopify.dev/docs/themes/tools/theme-check

## Scripts (bundled in this skill folder — not runnable on this phone)

The upstream skill's helper scripts (`scripts/shopify_init.py` for interactive
project scaffolding, `scripts/shopify_graphql.py` for the `ShopifyGraphQL`
query/pagination/rate-limit helpers) ship in this skill folder, but on this
phone they are not directly runnable — there is no script file on disk for the
agent to run. Scaffold with `shopify app init` instead, and use the GraphQL
patterns above (cursor pagination, `userErrors` on every mutation, bulk
operations over 250 items, exponential backoff on 429) directly.

## Verify before delivery

- Test the finished work in a real development store, and check the exact merchant flow the request named, from first click to saved result.

- Report what was verified in the store, what was only code checked, and what still needs the merchant to confirm.

## Alternatives for better results

- Prefer a theme extension over a theme fork when the change is one storefront feature, so the merchant keeps theme updates.

- Prefer an existing Shopify function or extension point over a custom backend when Shopify already enforces the rule natively.
