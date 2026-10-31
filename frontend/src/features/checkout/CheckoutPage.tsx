import { useEffect, useMemo, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../shared/api';
import { shippingSchema } from '../../shared/forms';
import { Field } from '../../shared/ui';
import type { Address } from '../account/AccountPage';
import { useCart } from '../cart/cartApi';
import { CheckoutCodView } from './CheckoutCodView';
import { checkoutKey, forgetCheckoutKey, placeOrder } from './checkoutApi';
import type { Shipping } from './checkoutApi';

const fromAddress = (a: Address): Shipping => ({ name: a.recipientName, phone: a.phone, address: `${a.line}, ${a.city}` });

export function CheckoutPage() {
  const cart = useCart();
  const client = useQueryClient();
  const navigate = useNavigate();
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: () => api<Address[]>('/api/users/me/addresses') });
  const form = useForm<Shipping>({ resolver: zodResolver(shippingSchema), mode: 'onTouched', defaultValues: { name: '', phone: '', address: '' } });
  const { errors } = form.formState;
  const shipping = shippingSchema.safeParse(form.watch());
  const [chosen, setChosen] = useState(false);

  useEffect(() => {
    const saved = addresses.data?.find((a) => a.isDefault) ?? addresses.data?.[0];
    if (saved && !chosen) form.reset(fromAddress(saved));
  }, [addresses.data, chosen, form]);

  const revision = cart.data?.snapshot.revision;
  const key = useMemo(() => revision === undefined ? '' : checkoutKey(revision), [revision]);

  if (cart.isPending) return <CheckoutCodView state={{ status: 'loading' }} />;
  if (cart.error) return <CheckoutCodView state={{ status: 'unavailable' }} />;
  const { snapshot, storeNames } = cart.data;
  if (snapshot.items.length === 0) return <CheckoutCodView state={{ status: 'empty' }} />;

  const edited = { onChange: () => setChosen(true) };

  return <div className="stack">
    <section className="panel stack">
      <h2>Địa chỉ nhận hàng</h2>
      {(addresses.data?.length ?? 0) > 0 && <label className="field">Địa chỉ đã lưu
        <select defaultValue="" onChange={(e) => {
          const picked = addresses.data?.find((a) => a.id === e.currentTarget.value);
          if (picked) { setChosen(true); form.reset(fromAddress(picked)); }
        }}>
          <option value="">Chọn địa chỉ…</option>
          {addresses.data?.map((a) => <option key={a.id} value={a.id}>{a.recipientName} — {a.line}, {a.city}</option>)}
        </select></label>}
      <Field label="Người nhận" error={errors.name}><input {...form.register('name', edited)} /></Field>
      <Field label="Số điện thoại" error={errors.phone}><input {...form.register('phone', edited)} /></Field>
      <Field label="Địa chỉ" error={errors.address}><input {...form.register('address', edited)} /></Field>
      {!shipping.success && <p className="muted" role="status">Nhập đủ người nhận, số điện thoại và địa chỉ để đặt hàng.</p>}
    </section>
    <CheckoutCodView state={{ status: 'ready', cart: snapshot, idempotencyKey: key }} storeNames={storeNames}
      onConfirm={shipping.success ? async (request) => {
        const result = await placeOrder(request, shipping.data);
        if (result.status === 'confirmed') {
          forgetCheckoutKey(snapshot.revision);
          await client.invalidateQueries({ queryKey: ['cart'] });
          navigate(`/orders/${result.orderId}`, { replace: true });
        }
        // A rejected attempt is final for its key: the next visit must start a new attempt.
        if (result.status === 'rejected') forgetCheckoutKey(snapshot.revision);
        return result;
      } : undefined} />
    <Link to="/cart">← Quay lại giỏ hàng</Link>
  </div>;
}
