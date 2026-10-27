import { useId, useLayoutEffect, useRef, useState } from 'react';
import type { DemoImage } from './demoTypes';
import { validateDemoImageFiles } from './demoImageValidation';

interface Props {
  images: readonly DemoImage[];
  onChange: (images: readonly DemoImage[]) => boolean;
  disabled?: boolean;
}

export function DemoImagePicker({ images, onChange, disabled = false }: Props) {
  const id = useId();
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const generation = useRef(0);
  const controller = useRef<AbortController | null>(null);
  const pendingUrls = useRef(new Set<string>());
  const latest = useRef({ images, onChange, disabled });
  latest.current = { images, onChange, disabled };

  useLayoutEffect(() => {
    generation.current++;
    controller.current?.abort();
    setPending(false);
    setError('');
    return () => {
      generation.current++;
      controller.current?.abort();
      for (const url of pendingUrls.current) URL.revokeObjectURL(url);
      pendingUrls.current.clear();
    };
  }, [images, disabled]);

  async function select(files: readonly File[]) {
    if (latest.current.disabled || !files.length) return;
    controller.current?.abort();
    const active = new AbortController();
    controller.current = active;
    const token = ++generation.current;
    const source = latest.current.images;
    const current = () => token === generation.current && !active.signal.aborted && !latest.current.disabled && source === latest.current.images;
    setPending(true);
    setError('');
    const urls: string[] = [];
    try {
      const result = await validateDemoImageFiles(files, source.length, active.signal);
      if (!current()) return;
      if (!result.ok) { setError(result.error); return; }
      const added = result.files.map((file): DemoImage => {
        const url = URL.createObjectURL(file);
        urls.push(url);
        pendingUrls.current.add(url);
        return { id: crypto.randomUUID(), url, name: file.name, mediaType: file.type, sizeBytes: file.size };
      });
      if (!latest.current.onChange([...source, ...added])) {
        setError('Không thể giữ ảnh trong phiên demo hiện tại. Hãy thử lại.');
        return;
      }
      for (const url of urls) pendingUrls.current.delete(url);
      urls.length = 0;
    } catch {
      if (current()) setError('Không thể tạo ảnh xem trước. Hãy thử lại.');
    } finally {
      for (const url of urls) {
        if (pendingUrls.current.delete(url)) URL.revokeObjectURL(url);
      }
      if (current()) setPending(false);
    }
  }

  function remove(imageId: string) {
    if (disabled || pending) return;
    try {
      if (!onChange(images.filter((image) => image.id !== imageId))) setError('Không thể xóa ảnh trong phiên demo hiện tại.');
    } catch { setError('Không thể xóa ảnh trong phiên demo hiện tại.'); }
  }

  return <section className="demo-image-picker" aria-label="Ảnh sản phẩm" aria-busy={pending}>
    <p>Ảnh xem trước trong phiên demo • chưa upload lên máy chủ</p>
    <div className="demo-image-drop" onDragOver={(event) => event.preventDefault()} onDrop={(event) => {
      event.preventDefault();
      if (!disabled && !pending) void select(Array.from(event.dataTransfer.files));
    }}>
      <label htmlFor={id}>Chọn hoặc thả ảnh sản phẩm</label>
      <input id={id} type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" multiple disabled={disabled || pending || images.length >= 5} aria-invalid={!!error} aria-describedby={`${id}-help${error ? ` ${id}-error` : ''}`} onChange={(event) => {
        const files = Array.from(event.currentTarget.files ?? []);
        event.currentTarget.value = '';
        void select(files);
      }} />
      <small id={`${id}-help`}>JPEG, PNG, WebP · tối đa 5 ảnh · 2 MiB/ảnh · 8.192 px/chiều và 16 triệu điểm ảnh.</small>
    </div>
    {pending && <p role="status">Đang kiểm tra ảnh…</p>}
    {error && <p id={`${id}-error`} className="demo-error" role="alert">{error}</p>}
    <div className="demo-image-grid">{images.map((image) => <figure className="demo-image-thumb" key={image.id}>
      <img src={image.url} alt={image.name} />
      <figcaption>{image.name} · {new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(image.sizeBytes / 1024)} KiB</figcaption>
      <button className="demo-button secondary" type="button" disabled={disabled || pending} aria-label={`Xóa ảnh ${image.name}`} onClick={() => remove(image.id)}>Xóa ảnh</button>
    </figure>)}</div>
  </section>;
}
