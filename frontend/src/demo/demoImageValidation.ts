type Validation = { ok: true; files: readonly File[] } | { ok: false; error: string };
const formats: Record<string, RegExp> = { 'image/jpeg': /\.jpe?g$/i, 'image/png': /\.png$/i, 'image/webp': /\.webp$/i };

function signature(bytes: Uint8Array, type: string): boolean {
  if (type === 'image/png') return [137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => bytes[index] === value);
  if (type === 'image/jpeg') return bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  return bytes.length >= 16 && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP' && ['VP8 ', 'VP8L', 'VP8X'].includes(String.fromCharCode(...bytes.slice(12, 16)));
}

async function dimensions(file: File, signal?: AbortSignal): Promise<{ width: number; height: number }> {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(file);
    try { return { width: bitmap.width, height: bitmap.height }; }
    finally { bitmap.close(); }
  }
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new Error()); return; }
    const image = new Image();
    const url = URL.createObjectURL(file);
    const finish = (ok: boolean) => {
      clearTimeout(timer);
      signal?.removeEventListener('abort', cancel);
      image.onload = image.onerror = null;
      URL.revokeObjectURL(url);
      if (ok) resolve({ width: image.naturalWidth, height: image.naturalHeight });
      else { image.src = ''; reject(new Error()); }
    };
    const cancel = () => finish(false);
    const timer = setTimeout(cancel, 10000);
    image.onload = () => finish(true);
    image.onerror = cancel;
    signal?.addEventListener('abort', cancel, { once: true });
    image.src = url;
  });
}

export async function validateDemoImageFiles(files: readonly File[], existingCount: number, signal?: AbortSignal): Promise<Validation> {
  if (!files.length) return { ok: false, error: 'Hãy chọn ít nhất một ảnh.' };
  if (!Number.isInteger(existingCount) || existingCount < 0 || existingCount + files.length > 5) return { ok: false, error: 'Mỗi sản phẩm có tối đa 5 ảnh xem trước.' };
  try {
    for (const file of files) {
      if (!file.size || file.size > 2 * 1024 * 1024) return { ok: false, error: 'Mỗi ảnh phải lớn hơn 0 byte và không vượt quá 2 MiB.' };
      if (!formats[file.type]?.test(file.name)) return { ok: false, error: 'Chỉ nhận JPEG, PNG hoặc WebP có đuôi tệp và loại ảnh khớp nhau.' };
      const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());
      if (!signature(bytes, file.type)) return { ok: false, error: 'Nội dung tệp không khớp định dạng ảnh đã chọn.' };
      if (signal?.aborted) return { ok: false, error: 'Đã hủy lựa chọn ảnh.' };
      const { width, height } = await dimensions(file, signal);
      if (signal?.aborted) return { ok: false, error: 'Đã hủy lựa chọn ảnh.' };
      if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || width * height > 16000000) return { ok: false, error: 'Ảnh không được vượt 8.192 px mỗi chiều hoặc 16 triệu điểm ảnh.' };
    }
    return { ok: true, files };
  } catch {
    return { ok: false, error: 'Không thể đọc ảnh. Hãy chọn tệp JPEG, PNG hoặc WebP hợp lệ.' };
  }
}
