import { useState, useRef } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { toIsoVN, toVNInput as vnInput } from './voucherTime';
import { Badge, dateTime, ErrorNote, Loaded, money, useAction } from '../../shared/ui';

type State = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'EXPIRED' | 'CANCELLED';
interface Voucher {
  readonly id:string; readonly code:string; readonly name:string; readonly discountType:'FIXED'|'PERCENT';
  readonly discountValue:number; readonly maxDiscount:number|null; readonly minOrderAmount:number;
  readonly usageLimit:number; readonly startsAt:string; readonly endsAt:string; readonly status:State; readonly version:string;
}
interface Limit {
  readonly used:number; readonly limit:number; readonly defaultLimit:number;
  readonly grant:{readonly validFrom:string;readonly validUntil:string}|null;
}
interface Request {
  readonly id:string; readonly storeId:string; readonly requestedLimit:number; readonly reason:string;
  readonly requestedFrom:string;readonly requestedUntil:string;
  readonly status:'PENDING'|'APPROVED'|'REJECTED'|'CANCELLED'; readonly version:string;
  readonly decisionReason:string|null;readonly createdAt:string;
}
const statuses:State[]=['DRAFT','SCHEDULED','ACTIVE','PAUSED','EXPIRED','CANCELLED'];

type VoucherForm = {
  code:string;name:string;discountType:'FIXED'|'PERCENT';discountValue:string;
  maxDiscount:string;minOrderAmount:string;usageLimit:string;startsAt:string;endsAt:string;
};
function VoucherEditor({ row, close }: {readonly row:Voucher|null;readonly close:()=>void}) {
  const [form,setForm]=useState<VoucherForm>(row?{
    code:row.code,name:row.name,discountType:row.discountType,discountValue:String(row.discountValue),
    maxDiscount:String(row.maxDiscount??50000),minOrderAmount:String(row.minOrderAmount),
    usageLimit:String(row.usageLimit),startsAt:vnInput(row.startsAt),endsAt:vnInput(row.endsAt),
  }:{code:'',name:'',discountType:'FIXED',discountValue:'20000',maxDiscount:'50000',
     minOrderAmount:'100000',usageLimit:'100',startsAt:'',endsAt:''});
  const [error,setError]=useState<string|null>(null);
  const set=(key:keyof VoucherForm,value:string)=>setForm(prev=>({...prev,[key]:value}));
  const save=useAction((body:object)=>api<Voucher>(
    row?'/api/stores/me/vouchers/'+row.id:'/api/stores/me/vouchers',
    {method:row?'PUT':'POST',body}),['voucher'],close);
  function submit(e:FormEvent) {
    e.preventDefault();setError(null);
    try {
      const body={
        code:form.code.trim().toUpperCase(),name:form.name.trim(),discountType:form.discountType,
        discountValue:Number(form.discountValue),minOrderAmount:Number(form.minOrderAmount),
        usageLimit:Number(form.usageLimit),startsAt:toIsoVN(form.startsAt),endsAt:toIsoVN(form.endsAt),
        ...(form.discountType==='PERCENT'?{maxDiscount:Number(form.maxDiscount)}:{}),
        ...(row?{expectedVersion:row.version}:{}),
      };
      save.mutate(body);
    } catch(err) {setError(err instanceof Error?err.message:'Dữ liệu không hợp lệ');}
  }
  return <form className="panel stack" onSubmit={submit}>
    <h2>{row?'Sửa voucher nháp':'Tạo voucher mới'}</h2>
    <label className="field">Mã voucher (4–20 ký tự A–Z/0–9)
      <input required value={form.code} maxLength={20} onChange={e=>set('code',e.currentTarget.value)}/></label>
    <label className="field">Tên chương trình<input required value={form.name} maxLength={120}
      onChange={e=>set('name',e.currentTarget.value)}/></label>
    <label className="field">Kiểu giảm<select value={form.discountType} onChange={e=>
      set('discountType',e.currentTarget.value as VoucherForm['discountType'])}>
      <option value="FIXED">Giảm tiền (VND)</option><option value="PERCENT">Giảm theo phần trăm</option>
    </select></label>
    <label className="field">Giá trị giảm<input required type="number" step="1" min="1"
      value={form.discountValue} onChange={e=>set('discountValue',e.currentTarget.value)}/></label>
    {form.discountType==='PERCENT'&&<label className="field">Giảm tối đa (VND)
      <input type="number" required min="1" step="1" value={form.maxDiscount}
        onChange={e=>set('maxDiscount',e.currentTarget.value)}/></label>}
    <label className="field">Đơn tối thiểu (VND)<input type="number" required min="0" step="1"
      value={form.minOrderAmount} onChange={e=>set('minOrderAmount',e.currentTarget.value)}/></label>
    <label className="field">Số lượt được phép (chỉ lưu, chưa dùng Checkout)
      <input type="number" required min="1" max="100000" step="1" value={form.usageLimit}
        onChange={e=>set('usageLimit',e.currentTarget.value)}/></label>
    <label className="field">Bắt đầu (giờ Việt Nam)<input type="datetime-local" required
      value={form.startsAt} onChange={e=>set('startsAt',e.currentTarget.value)}/></label>
    <label className="field">Kết thúc (giờ Việt Nam)<input type="datetime-local" required
      value={form.endsAt} onChange={e=>set('endsAt',e.currentTarget.value)}/></label>
    {error&&<p role="alert">{error}</p>}<ErrorNote error={save.error}/>
    <div className="row row--wrap">
      <button disabled={save.isPending} className="btn btn--primary">Lưu bản nháp</button>
      <button type="button" className="btn" onClick={close}>Đóng</button>
    </div>
  </form>;
}
function VoucherCard({row,onEdit}:{readonly row:Voucher;readonly onEdit:(row:Voucher)=>void}){
  const act=useAction((action:string)=>api<Voucher>(
    '/api/stores/me/vouchers/'+row.id+'/'+action,
    {method:'POST',body:{expectedVersion:row.version}}),['voucher']);
  const actions=row.status==='DRAFT'?['publish','cancel']:
    row.status==='SCHEDULED'||row.status==='ACTIVE'?['pause','cancel']:
    row.status==='PAUSED'?['resume','cancel']:[];
  return <article className="panel stack">
    <div className="row row--between"><strong>{row.code} — {row.name}</strong><span>{row.status}</span></div>
    <p>{row.discountType==='FIXED'?'Giảm '+money(row.discountValue):
      'Giảm '+row.discountValue+'%, tối đa '+money(row.maxDiscount)}
      {' · Đơn từ '+money(row.minOrderAmount)}</p>
    <small>{dateTime(row.startsAt)} đến {dateTime(row.endsAt)} · Quota sử dụng {row.usageLimit}</small>
    <div className="row row--wrap">
      {row.status==='DRAFT'&&<button className="btn" type="button" onClick={()=>onEdit(row)}>Sửa</button>}
      {actions.map(action=><button key={action} className="btn" type="button" disabled={act.isPending}
        onClick={()=>act.mutate(action)}>{action==='publish'?'Công bố':action==='pause'?'Tạm dừng':
          action==='resume'?'Bật lại':'Hủy'}</button>)}
    </div><ErrorNote error={act.error}/>
  </article>;
}
function RequestForm(){
  const [limit,setLimit]=useState('25'); const [reason,setReason]=useState('');
  const [budget,setBudget]=useState('');const [from,setFrom]=useState('');
  const [until,setUntil]=useState('');const [note,setNote]=useState('');
  const key=useRef(crypto.randomUUID());
  const act=useAction((body:object)=>api<Request>('/api/stores/me/voucher-limit-requests',
    {method:'POST',body,headers:{'Idempotency-Key':key.current}}),['voucher'],()=>{
      key.current=crypto.randomUUID();setNote('Đã gửi yêu cầu tới Admin.');
    });
  function submit(e:FormEvent) {
    e.preventDefault();setNote('');
    try{act.mutate({requestedLimit:Number(limit),reason:reason.trim(),
      ...(budget.trim()?{budgetNote:budget.trim()}:{}),
      requestedFrom:toIsoVN(from),requestedUntil:toIsoVN(until)});}
    catch{setNote('Thời gian yêu cầu không hợp lệ');}
  }
  return <form className="panel stack" onSubmit={submit}>
    <h2>Yêu cầu mở rộng hạn mức</h2>
    <p className="muted">Tối đa 50 chương trình trong tối đa 30 ngày, chỉ hiệu lực khi Admin duyệt.</p>
    <label className="field">Tổng slot xin cấp<input required type="number" min="21" max="50" step="1"
      value={limit} onChange={e=>setLimit(e.currentTarget.value)}/></label>
    <label className="field">Lý do<input required maxLength={500} value={reason}
      onChange={e=>setReason(e.currentTarget.value)}/></label>
    <label className="field">Ngân sách dự kiến (tùy chọn)<input maxLength={500} value={budget}
      onChange={e=>setBudget(e.currentTarget.value)}/></label>
    <label className="field">Từ ngày (giờ Việt Nam)<input required type="datetime-local" value={from}
      onChange={e=>setFrom(e.currentTarget.value)}/></label>
    <label className="field">Đến ngày (giờ Việt Nam)<input required type="datetime-local" value={until}
      onChange={e=>setUntil(e.currentTarget.value)}/></label>
    {note&&<p role="status">{note}</p>}<ErrorNote error={act.error}/>
    <button className="btn btn--primary" disabled={act.isPending}>Gửi yêu cầu</button>
  </form>;
}
function StoreRequest({item}:{readonly item:Request}){
  const cancel=useAction(()=>api<Request>('/api/stores/me/voucher-limit-requests/'+item.id+'/cancel',
    {method:'POST',body:{expectedVersion:item.version}}),['voucher']);
  return <li><strong>{item.requestedLimit} slot</strong> · <Badge value={item.status}/>
    <small> · {dateTime(item.createdAt)} · {item.reason}</small>
    {item.decisionReason&&<p>Lý do xét duyệt: {item.decisionReason}</p>}
    {item.status==='PENDING'&&<button type="button" className="btn" disabled={cancel.isPending}
      onClick={()=>cancel.mutate()}>Hủy yêu cầu</button>}
    <ErrorNote error={cancel.error}/></li>;
}
export function SellerVouchersPage(){
  const [editing,setEditing]=useState<Voucher|null|undefined>(undefined);
  const [filter,setFilter]=useState('');
  const voucher=useQuery({queryKey:['voucher','list',filter],queryFn:()=>api<Voucher[]>(
    '/api/stores/me/vouchers?size=50'+(filter?'&status='+encodeURIComponent(filter):''))});
  const limit=useQuery({queryKey:['voucher','limit'],queryFn:()=>api<Limit>('/api/stores/me/voucher-limit')});
  const requests=useQuery({queryKey:['voucher','requests'],queryFn:()=>api<Request[]>(
    '/api/stores/me/voucher-limit-requests?size=50')});
  return <div className="stack"><h1>Voucher cửa hàng</h1>
    <p className="muted">C1 chỉ quản lý voucher. Chưa thể dùng mã để giảm tiền tại Checkout.</p>
    <div className="panel stack"><h2>Hạn mức sử dụng</h2>
      <Loaded query={limit}>{item=><p><strong>{item.used}/{item.limit}</strong> slot
        {item.grant&&' · Grant đến '+dateTime(item.grant.validUntil)}</p>}</Loaded></div>
    <button type="button" className="btn btn--primary" onClick={()=>setEditing(null)}>Tạo voucher</button>
    {editing!==undefined&&<VoucherEditor key={editing?.id??'new'} row={editing} close={()=>setEditing(undefined)}/>}
    <label className="field">Trạng thái<select value={filter} onChange={e=>setFilter(e.currentTarget.value)}>
      <option value="">Tất cả</option>{statuses.map(s=><option key={s} value={s}>{s}</option>)}
    </select></label>
    <Loaded query={voucher} empty={rows=>rows.length===0}>{rows=><div className="stack">
      {rows.map(row=><VoucherCard key={row.id} row={row} onEdit={setEditing}/>)}</div>}</Loaded>
    <RequestForm/><h2>Yêu cầu nâng hạn mức</h2>
    <Loaded query={requests} empty={rows=>rows.length===0}>{rows=><ul className="stack">
      {rows.map(item=><StoreRequest key={item.id} item={item}/>)}</ul>}</Loaded>
  </div>;
}
function DecisionCard({item}:{readonly item:Request}){
  const [limit,setLimit]=useState(String(item.requestedLimit));
  const [from,setFrom]=useState(vnInput(item.requestedFrom));
  const [until,setUntil]=useState(vnInput(item.requestedUntil));
  const [reason,setReason]=useState(''); const [note,setNote]=useState('');
  const act=useAction((input:{action:string;body:object})=>
    api<Request>('/api/admin/voucher-limit-requests/'+item.id+'/'+input.action,
      {method:'POST',body:input.body}),['admin-vouchers']);
  function approve(){
    setNote('');
    try{act.mutate({action:'approve',body:{expectedVersion:item.version,
      approvedLimit:Number(limit),approvedFrom:toIsoVN(from),approvedUntil:toIsoVN(until),
      ...(reason.trim()?{reason:reason.trim()}: {})}});}
    catch{setNote('Thời gian phê duyệt chưa hợp lệ');}
  }
  return <article className="panel stack">
    <h2>Store {item.storeId.slice(0,8)} — Xin {item.requestedLimit} slot</h2>
    <p>{item.reason}</p>
    <small>{dateTime(item.requestedFrom)} đến {dateTime(item.requestedUntil)}</small>
    <label className="field">Số slot duyệt<input type="number" min="21" max={item.requestedLimit}
      value={limit} onChange={e=>setLimit(e.currentTarget.value)}/></label>
    <label className="field">Hiệu lực từ (VN)<input type="datetime-local" value={from}
      onChange={e=>setFrom(e.currentTarget.value)}/></label>
    <label className="field">Hiệu lực đến (VN)<input type="datetime-local" value={until}
      onChange={e=>setUntil(e.currentTarget.value)}/></label>
    <label className="field">Lý do khi rút ngắn/từ chối<input maxLength={500} value={reason}
      onChange={e=>setReason(e.currentTarget.value)}/></label>
    <div className="row row--wrap">
      <button type="button" className="btn btn--primary" disabled={act.isPending} onClick={approve}>Duyệt</button>
      <button type="button" className="btn" disabled={act.isPending||!reason.trim()} onClick={()=>
        act.mutate({action:'reject',body:{expectedVersion:item.version,reason:reason.trim()}})}>Từ chối</button>
    </div>{note&&<p role="alert">{note}</p>}<ErrorNote error={act.error}/>
  </article>;
}
export function AdminVoucherLimitsPage(){
  const pending=useQuery({queryKey:['admin-vouchers','pending'],
    queryFn:()=>api<Request[]>('/api/admin/voucher-limit-requests?status=PENDING&size=50')});
  return <div className="stack"><h1>Xét duyệt nâng hạn mức voucher</h1>
    <p className="muted">Duyệt quyền tạo chương trình, không cấp sẵn mã giảm giá hay ảnh hưởng Checkout.</p>
    <Loaded query={pending} empty={rows=>rows.length===0}>{rows=><div className="stack">
      {rows.map(item=><DecisionCard key={item.id} item={item}/>)}</div>}</Loaded>
  </div>;
}
