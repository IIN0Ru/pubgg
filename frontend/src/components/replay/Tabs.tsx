interface Props<T extends string | number> {
  label: string;
  value: T;
  options: ReadonlyArray<{ value: T; label: string }>;
  onChange: (value: T) => void;
  className?: string;
  title?: string;
  hidden?: boolean;
}

/** 하나만 켜지는 버튼 묶음 (main.css 의 .tabs / .tab) */
export function Tabs<T extends string | number>({ label, value, options, onChange, className, title, hidden }: Props<T>) {
  return (
    <div className={className ? `tabs ${className}` : 'tabs'} role="group" aria-label={label} title={title} hidden={hidden}>
      {options.map((o) => (
        <button
          key={String(o.value)}
          type="button"
          className={o.value === value ? 'tab is-active' : 'tab'}
          aria-pressed={o.value === value}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
