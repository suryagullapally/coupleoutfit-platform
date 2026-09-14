/** Formats integer minor units using the currency precision defined by Intl/ISO 4217.
 * Invalid amounts, unsupported currencies, and invalid locales throw RangeError.
 */
export function formatMoney(amountMinor: number, currencyCode: string, locale: string): string {
  if (!Number.isSafeInteger(amountMinor)) throw new RangeError("Amount must be a safe integer in minor units.");
  const currency = currencyCode.toUpperCase();
  if (!Intl.supportedValuesOf("currency").includes(currency)) throw new RangeError("Unsupported currency code.");
  if (!locale || Intl.NumberFormat.supportedLocalesOf([locale]).length === 0) {
    throw new RangeError("Unsupported locale.");
  }
  const formatter = new Intl.NumberFormat(locale, { style: "currency", currency });
  const digits = formatter.resolvedOptions().maximumFractionDigits;
  if (digits === undefined) throw new RangeError("Currency precision is unavailable.");
  return formatter.format(amountMinor / 10 ** digits);
}
