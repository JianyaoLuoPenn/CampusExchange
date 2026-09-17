import { test, expect } from "@playwright/test";
// Requires the real backend with DEMO_DATA=true and PAYMENT_MODE=mock.
test("browse, filter, publish and reserve a single item through the real API", async ({
  page,
  browser,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(page.getByText("SIMULATION MODE")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Find your next everyday favorite" }),
  ).toBeVisible();
  await page.getByLabel("Apartment (exact name)").fill("Impossible Apartment");
  await expect(
    page.getByRole("heading", { name: "No finds just yet" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Clear filters" }).click();
  await expect(
    page.getByRole("heading", { name: "Oak study desk" }),
  ).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/marketplace-desktop.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await page.getByLabel(/^Email/).fill("maya@example.test");
  await page.getByLabel(/^Password/).fill("CampusDemo123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Sign out · Maya" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "+ List an item" }).click();
  const title = "Browser-tested lamp " + Date.now();
  await page.getByLabel("Item title").fill(title);
  await page
    .getByLabel("Description, including any wear")
    .fill("Fictional UI integration test listing");
  await page.getByLabel(/^Campus/).fill("North Campus");
  await page.getByLabel(/^Apartment/).fill("Maple Court");
  await page.getByLabel("Public pickup area").fill("Lobby");
  await page.getByLabel("Private pickup address").fill("PRIVATE TEST ADDRESS");
  const future = new Date(Date.now() + 86400000);
  const local = new Date(future.getTime() - future.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
  await page.getByLabel("Pickup option 1").fill(local);
  await page.getByLabel("Total price").fill("25");
  await page.getByLabel("Require a refundable deposit").check();
  await page.getByLabel(/^Deposit \(USD\)/).fill("5");
  await page
    .getByRole("button", { name: "Publish listing", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  const listingUrl = page.url();
  await expect(page.getByText("PRIVATE TEST ADDRESS")).toHaveCount(0);
  const buyerContext = await browser.newContext();
  const buyer = await buyerContext.newPage();
  await buyer.goto(new URL("/signin", page.url()).href);
  await buyer.getByLabel(/^Email/).fill("alex@example.test");
  await buyer.getByLabel(/^Password/).fill("CampusDemo123!");
  await buyer.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    buyer.getByRole("heading", { name: "Find your next everyday favorite" }),
  ).toBeVisible();
  await buyer.goto(listingUrl);
  await buyer.getByLabel("Choose a pickup time").click();
  await buyer.getByRole("option").first().click();
  await buyer
    .getByRole("button", { name: "Reserve for pickup", exact: true })
    .click();
  const booking = buyer
    .locator("article")
    .filter({ has: buyer.getByRole("heading", { name: title }) });
  await expect(
    booking.getByText("pending payment", { exact: true }),
  ).toBeVisible();
  await expect(booking.getByText("PRIVATE TEST ADDRESS")).toHaveCount(0);
  await booking
    .getByRole("button", { name: "Simulate payment success" })
    .click();
  await expect(booking.getByText("Pickup: PRIVATE TEST ADDRESS")).toBeVisible();
  await booking.getByRole("button", { name: "Cancel reservation" }).click();
  await expect(booking.getByText("Simulated deposit: refunded")).toBeVisible({
    timeout: 20000,
  });
  await buyerContext.close();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Good finds. Closer to home." }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Oak study desk" }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    )
    .toBe(true);
  await page.screenshot({
    path: "../docs/screenshots/marketplace-mobile.png",
    fullPage: true,
  });
  expect(errors).toEqual([]);
});

test("an expired saved session does not block public browsing or signing in again", async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem("campus-token", "expired-or-replaced-token");
    sessionStorage.setItem(
      "campus-user",
      JSON.stringify({ id: 99999, name: "Old session" }),
    );
  });
  await page.goto("/");
  await expect(page.getByText("SIMULATION MODE")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Oak study desk" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await page.getByLabel(/^Email/).fill("alex@example.test");
  await page.getByLabel(/^Password/).fill("CampusDemo123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Sign out · Alex" }),
  ).toBeVisible();
});
