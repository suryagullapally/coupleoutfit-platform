import { test } from "node:test";
import assert from "node:assert/strict";
import { formatMoney } from "../lib/money.ts";
import { defaultMarket } from "../config/market.ts";

test("formats central market defaults, zero and negative values", () => {
  const { currencyCode, localeCode } = defaultMarket;
  assert.equal(formatMoney(12345, currencyCode, localeCode), new Intl.NumberFormat(localeCode, { style: "currency", currency: currencyCode }).format(123.45));
  assert.equal(formatMoney(0, "USD", "en-US"), "$0.00");
  assert.equal(formatMoney(-123, "USD", "en-US"), "-$1.23");
});

test("respects zero and three minor digits and localized separators", () => {
  assert.equal(formatMoney(1234, "JPY", "en-US"), "¥1,234");
  assert.equal(formatMoney(1234, "KWD", "en-US"), "KWD\u00a01.234");
  assert.equal(formatMoney(12345, "EUR", "de-DE"), "123,45\u00a0€");
});

test("rejects invalid amounts, unsupported currencies and invalid locales", () => {
  for (const amount of [NaN, Infinity, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => formatMoney(amount, "USD", "en-US"), RangeError);
  }
  for (const currency of ["", "NOPE", "ZZZ"]) assert.throws(() => formatMoney(100, currency, "en-US"), RangeError);
  for (const locale of ["", "invalid_locale", "zz-ZZ"]) assert.throws(() => formatMoney(100, "USD", locale), RangeError);
});
