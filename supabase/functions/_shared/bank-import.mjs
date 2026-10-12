/**
 * Strict, side-effect-free bank transaction adapter for a future SERVER-ONLY importer.
 * NO account linking, secrets, API calls or database writes are performed here.
 * This file must never be used to accept untrusted user-supplied 'bank transactions'.
 * The caller must first authenticate to Akahu/Basiq server-side and verify account ownership,
 * active read-only consent, pagination completion and the account's actual currency.
 */

const PROVIDER_COUNTRIES = Object.freeze({ akahu: 'NZ', basiq: 'AU' });
const MAX_DESCRIPTION = 500;
const MAX_ID = 256;

function nonEmptyString(value, field, max = MAX_ID) {
  if (typeof value !== 'string' || value.length === 0 || value.length > max || !value.trim() || /[\u0000-\u001F\u007F]/u.test(value)) {
    throw new TypeError(`Invalid ${field}`);
  }
  return value;
}

export function decimalToCents(value) {
  if (typeof value !== 'number' && typeof value !== 'string') throw new TypeError('Invalid amount');
  if (typeof value === 'number' && !Number.isFinite(value)) throw new TypeError('Invalid amount');
  const input = String(value);
  const match = /^(-?)(0|[1-9][0-9]{0,14})(?:\.([0-9]{1,2}))?$/.exec(input);
  if (!match) throw new TypeError('Invalid amount (must have at most two decimal places)');
  const cents = BigInt(match[2]) * 100n + BigInt((match[3] || '').padEnd(2, '0'));
  const signed = match[1] === '-' ? -cents : cents;
  if (signed > BigInt(Number.MAX_SAFE_INTEGER) || signed < -BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new RangeError('Amount outside safe integer range');
  }
  return Number(signed);
}

function validTimestamp(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
    throw new TypeError('Invalid posted date');
  }
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new TypeError('Invalid posted date');
  return date.toISOString();
}

/**
 * Returns a deliberately small, privacy-minimised record for the staging ledger.
 * Amounts use signed integer cents: negative is spending, positive is income.
 * NZD and AUD are NEVER converted or aggregated into one balance.
 * Pending transactions are rejected to avoid pending/settled double counting.
 */
export function normalizeBankTransaction(provider, item, accountCurrency) {
  if (!Object.hasOwn(PROVIDER_COUNTRIES, provider)) throw new TypeError('Unknown provider');
  if (!item || typeof item !== 'object' || Array.isArray(item)) throw new TypeError('Invalid transaction');
  const currency = nonEmptyString(accountCurrency, 'account currency', 3);
  if (currency !== (provider === 'akahu' ? 'NZD' : 'AUD')) {
    // Other currencies require an explicit, reviewed multi-currency import design.
    throw new TypeError('Unsupported provider account currency');
  }
  let id, account, posted, description, cents;
  if (provider === 'akahu') {
    id = nonEmptyString(item._id, 'transaction ID');
    account = nonEmptyString(item._account, 'account ID');
    posted = validTimestamp(item.date);
    description = nonEmptyString(item.description, 'description', MAX_DESCRIPTION);
    cents = decimalToCents(item.amount);
  } else {
    if (item.status !== 'posted') throw new TypeError('Unposted or pending transaction');
    id = nonEmptyString(item.id, 'transaction ID');
    account = nonEmptyString(item.account, 'account ID');
    posted = validTimestamp(item.postDate);
    description = nonEmptyString(item.description, 'description', MAX_DESCRIPTION);
    cents = decimalToCents(item.amount);
    if (item.direction === 'debit' && cents > 0 || item.direction === 'credit' && cents < 0) {
      throw new TypeError('Amount conflicts with bank direction');
    }
  }
  // Do not store balances, account numbers, merchant metadata or raw bank payloads.
  return Object.freeze({ provider, providerAccountId: account, providerTransactionId: id,
    currency, amountCents: cents, postedAt: posted, description });
}

/** Validates duplicate identifiers in a single page/batch before a service-role upsert. */
export function normalizeBankBatch(provider, items, accountCurrency) {
  if (!Array.isArray(items) || items.length > 500) throw new TypeError('Invalid bounded batch');
  const records = new Map();
  for (const item of items) {
    const record = normalizeBankTransaction(provider, item, accountCurrency);
    const key = `${record.provider}\u0000${record.providerAccountId}\u0000${record.providerTransactionId}`;
    const previous = records.get(key);
    if (previous && JSON.stringify(previous) !== JSON.stringify(record)) throw new TypeError('Conflicting duplicate transaction ID');
    records.set(key, record);
  }
  return Array.from(records.values());
}

export const SUPPORTED_BANK_PROVIDERS = Object.freeze(PROVIDER_COUNTRIES);
