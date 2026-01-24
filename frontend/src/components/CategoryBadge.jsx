import { formatCategory } from '../utils/formatters';

/** 카테고리별 아이콘 + 색상 */
const CATEGORY_STYLES = {
  ELECTRONICS: { bg: 'bg-sky-50', text: 'text-sky-700', icon: '💻' },
  FASHION: { bg: 'bg-pink-50', text: 'text-pink-700', icon: '👗' },
  HOME: { bg: 'bg-amber-50', text: 'text-amber-700', icon: '🏠' },
  SPORTS: { bg: 'bg-green-50', text: 'text-green-700', icon: '⚽' },
  BOOKS: { bg: 'bg-indigo-50', text: 'text-indigo-700', icon: '📚' },
  OTHER: { bg: 'bg-gray-50', text: 'text-gray-600', icon: '📦' },
};

const DEFAULT = { bg: 'bg-gray-50', text: 'text-gray-600', icon: '📦' };

/**
 * 카테고리 뱃지 컴포넌트 (아이콘 포함)
 */
export default function CategoryBadge({ category }) {
  const style = CATEGORY_STYLES[category] || DEFAULT;

  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-semibold ${style.bg} ${style.text}`}>
      <span className="text-xs">{style.icon}</span>
      {formatCategory(category)}
    </span>
  );
}
