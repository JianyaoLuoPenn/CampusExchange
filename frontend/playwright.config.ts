import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: process.env.PLAYWRIGHT_EXTERNAL_URL || "http://localhost:5173",
    browserName: "chromium",
    ...(process.env.PLAYWRIGHT_CHROME_PATH
      ? {
          launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROME_PATH },
        }
      : {}),
  },
  webServer: process.env.PLAYWRIGHT_EXTERNAL_URL
    ? undefined
    : {
        command: "npm run dev -- --host 127.0.0.1",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
      },
});
