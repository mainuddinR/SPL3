export interface SuggestionSection { title: string; text: string; }

const headings: Record<string, string> = {
  Summary: 'Summary', Risk: 'Risk', Recommendation: 'Recommended remediation',
  Verification: 'Verification', Limitations: 'Limitations'
};

export function savedSuggestionSections(value: string): SuggestionSection[] {
  const parts = value.split(/\n\n(?=Risk\n|Recommendation\n|Verification\n|Limitations\n)/);
  const sections = parts.map(part => {
    const index = part.indexOf('\n');
    if (index < 0) return null;
    const heading = part.substring(0, index);
    return headings[heading] ? { title: headings[heading], text: part.substring(index + 1).trim() } : null;
  });
  return sections.length === 5 && sections.every(Boolean)
    ? sections as SuggestionSection[] : [{ title: 'Saved suggestion', text: value }];
}

export function primarySuggestionSections(value: string): SuggestionSection[] {
  return savedSuggestionSections(value).filter(section => section.title !== 'Limitations');
}

export function suggestionReviewNote(value: string): string | null {
  return savedSuggestionSections(value).find(section => section.title === 'Limitations')?.text ?? null;
}
