import { test } from "node:test";
import assert from "node:assert/strict";
import { homepageContent } from "../features/home/content.ts";

test("keeps the approved homepage promise and clear source-entry alternatives", () => {
  assert.equal(
    homepageContent.promise,
    "Paste or upload an outfit you like, and CoupleOutfit finds matching outfits for your partner from real products you can buy.",
  );
  assert.match(homepageContent.urlLabel, /product link/i);
  assert.match(homepageContent.uploadLabel, /upload.*image/i);
  assert.match(homepageContent.uploadHelp, /JPG, PNG or WebP/i);
  assert.match(homepageContent.uploadHelp, /10 MB/i);
});

test("presents the three-step partner matching story in order", () => {
  assert.deepEqual(
    homepageContent.steps.map(({ number, title }) => ({ number, title })),
    [
      { number: "01", title: "Choose an outfit" },
      { number: "02", title: "We read the look" },
      { number: "03", title: "Discover their match" },
    ],
  );
});

test("links only to the approved public legal routes", () => {
  assert.deepEqual(homepageContent.legalLinks, [
    { label: "Privacy", href: "/privacy" },
    { label: "Terms", href: "/terms" },
    { label: "Affiliate disclosure", href: "/affiliate-disclosure" },
  ]);
});
