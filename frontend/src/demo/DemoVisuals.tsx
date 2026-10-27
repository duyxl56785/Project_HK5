import { useId } from 'react';
import type { ProductArt } from './demoTypes';

export function DemoIcon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, string> = {
    grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    box: 'm3 7 9-4 9 4-9 4-9-4zm0 0v10l9 4 9-4V7M12 11v10M8 5l9 4',
    store: 'M3 10h18l-2-6H5l-2 6zm1 0v10h16V10M9 20v-7h6v7M3 10c0 4 4 4 4 0 0 4 5 4 5 0 0 4 5 4 5 0 0 4 4 4 4 0',
    users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3a4 4 0 0 1 0 8M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
    tree: 'M4 4h6v5H4z M14 15h6v5h-6z M3 15h6v5H3z M7 9v3h10v3M6 12v3',
    image: 'M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zm-1 14 6-6 4 4 3-3 5 5M8 7h.01',
    arrow: 'M5 12h14m-6-6 6 6-6 6',
    search: 'm21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    chevron: 'm9 5 7 7-7 7',
    check: 'm5 12 4 4L19 6',
    reset: 'M3 10a9 9 0 1 1 1 7M3 4v6h6',
    menu: 'M4 6h16M4 12h16M4 18h16',
    help: 'M9 9a3 3 0 1 1 5 2c-1 1-2 1-2 3M12 17h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
    leaf: 'M20 3C8 2 1 10 6 17s17 2 14-14zM5 21l10-11',
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] ?? paths.box} /></svg>;
}

export function ProductArtwork({ kind, label, variant = 0 }: { kind: ProductArt; label?: string; variant?: number }) {
  const id = useId().replace(/:/g, '');
  const palette = [['#eee7d9', '#c7ac8c', '#9d7d60'], ['#e5e9e0', '#9aa98a', '#738366'], ['#f1e4d9', '#c38c69', '#9a654d']][variant % 3];
  return <svg className="demo-art" viewBox="0 0 400 340" role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
    <defs><linearGradient id={`${id}bg`} x2="1" y2="1"><stop stopColor={palette[0]} /><stop offset="1" stopColor="#faf7f0" /></linearGradient><linearGradient id={`${id}body`}><stop stopColor={palette[1]} /><stop offset=".5" stopColor={palette[0]} /><stop offset="1" stopColor={palette[2]} /></linearGradient><filter id={`${id}s`}><feGaussianBlur stdDeviation="9" /></filter></defs>
    <rect width="400" height="340" fill={`url(#${id}bg)`} /><path d="M0 280 400 230v110H0" fill="#fff" opacity=".22" /><ellipse cx="205" cy="280" rx="91" ry="13" fill="#695a46" opacity=".16" filter={`url(#${id}s)`} />
    {kind === 'vase' && <><path d="M179 114h42c-5 36-6 48 16 77 44 58 32 95-37 95s-81-37-37-95c22-29 21-41 16-77Z" fill={`url(#${id}body)`} /><ellipse cx="200" cy="114" rx="22" ry="7" fill={palette[2]} /><path d="M198 113c-3-36 18-69 36-93M200 91c-40-7-51-23-51-23m63-6c24 4 42-4 50-15" stroke="#737858" strokeWidth="3" fill="none" /><path d="M220 42c-15-20-9-27-8-29 18 6 23 13 8 29M186 83c-18-26-32-24-39-19 2 19 16 25 39 19M234 58c1-21 15-26 29-22 0 18-11 25-29 22" fill="#7b8964" /></>}
    {kind === 'cup' && <><path d="M240 154c60-10 65 70 11 75" stroke={palette[2]} strokeWidth="15" fill="none" /><path d="M131 143h125l-9 112c-2 37-104 37-107 0Z" fill={`url(#${id}body)`} /><ellipse cx="194" cy="145" rx="62" ry="14" fill={palette[2]} /><ellipse cx="194" cy="143" rx="53" ry="8" fill="#695746" /><path d="M164 209h60M164 217h60" stroke="#c9b69b" opacity=".7" /><ellipse cx="194" cy="282" rx="80" ry="7" fill={palette[1]} /></>}
    {kind === 'lamp' && <><ellipse cx="201" cy="275" rx="59" ry="12" fill="#b8a787" /><path d="M201 270V122" stroke="#94815f" strokeWidth="13" /><path d="m161 66-64 121c38 27 171 27 209 0L242 66Z" fill={`url(#${id}body)`} /><ellipse cx="201" cy="66" rx="41" ry="10" fill="#cbbfa8" /><path d="M122 182h158M137 146h128M150 119h100M163 93h75" stroke="#a79474" opacity=".35" /></>}
    {kind === 'bag' && <><path d="M163 145v-34c0-53 77-53 77 0v34" fill="none" stroke="#a69674" strokeWidth="13" /><path d="m126 134-18 141c55 20 128 20 186 0l-18-141Z" fill={`url(#${id}body)`} /><path d="M150 145v119m22-119v125m23-125v128m23-128v125m23-125v119m22-119v111M121 174h160M116 202h170M113 230h176M110 257h182" stroke="#ab9b7f" opacity=".45" strokeWidth="2" /></>}
    {kind === 'linen' && <><path d="m111 127 138-34 67 130-149 57-78-90Z" fill={palette[2]} /><path d="m106 112 147-18 42 150-151 22Z" fill={`url(#${id}body)`} /><path d="m112 122 146-18m-141 35 146-18m-142 36 147-19m-143 36 147-18m-142 35 146-18m-142 36 147-18m-142 36 146-18" stroke="#fff" strokeWidth="1.5" opacity=".35" /><path d="m144 266 14 9m1-11 14 9m1-11 14 9m1-11 14 9m1-11 14 9m1-11 14 9" stroke={palette[1]} strokeWidth="3" /></>}
    {kind === 'plant' && <><path d="M201 210V82m0 61-35-37m35 70 38-36" stroke="#6a785b" strokeWidth="5" fill="none" /><path d="M199 120c-38-13-47-39-27-55 31 2 36 29 27 55m4 32c2-44 23-65 47-58 5 26-15 45-47 58m-3 39c-41-6-64-26-54-50 31-8 49 14 54 50m4-102c-18-26-10-45 4-55 23 15 17 38-4 55" fill="#748b65" /><path d="m149 201 13 70c5 22 74 22 79 0l13-70Z" fill={`url(#${id}body)`} /><ellipse cx="202" cy="202" rx="53" ry="11" fill={palette[2]} /></>}
  </svg>;
}
