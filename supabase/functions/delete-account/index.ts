import { createHandler } from './handler.mjs';

Deno.serve(createHandler({
  url: Deno.env.get('SUPABASE_URL'),
  adminKey: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),
  origin: 'https://frymastercheese.github.io',
  request: fetch,
}));
