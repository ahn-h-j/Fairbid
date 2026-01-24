import { formatPrice } from '../utils/formatters';

/**
 * 가격 표시 컴포넌트
 */
export default function PriceDisplay({ label, price, size = 'md', highlight = false }) {
  const sizeClasses = {
    lg: 'text-3xl sm:text-4xl font-extrabold',
    md: 'text-lg font-bold',
    sm: 'text-[14px] font-bold',
  };

  const priceColor = highlight ? 'text-blue-600' : 'text-gray-900';

  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[11px] text-gray-400 font-semibold uppercase tracking-wider">{label}</span>
      <span className={`${sizeClasses[size]} ${priceColor} tabular-nums`}>
        {formatPrice(price)}
      </span>
    </div>
  );
}
