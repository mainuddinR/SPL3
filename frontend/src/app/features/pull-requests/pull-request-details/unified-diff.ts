import { PullRequestFile, SatdAnalysisResponse } from '../../../models/pull-request.model';

export interface DiffRow {
  text: string;
  oldLine: number | null;
  newLine: number | null;
}

const HUNK_HEADER = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/;

export function parseUnifiedDiff(patch: string | null | undefined): DiffRow[] {
  if (!patch) return [];

  let oldLine = 0;
  let newLine = 0;
  let inHunk = false;
  const rows: DiffRow[] = [];

  for (const text of patch.split('\n')) {
    const hunk = HUNK_HEADER.exec(text);
    if (hunk) {
      oldLine = Number(hunk[1]);
      newLine = Number(hunk[2]);
      inHunk = true;
      rows.push({ text, oldLine: null, newLine: null });
    } else if (inHunk && text.startsWith(' ')) {
      rows.push({ text, oldLine: oldLine++, newLine: newLine++ });
    } else if (inHunk && text.startsWith('+')) {
      rows.push({ text, oldLine: null, newLine: newLine++ });
    } else if (inHunk && text.startsWith('-')) {
      rows.push({ text, oldLine: oldLine++, newLine: null });
    } else {
      // Headers, no-newline markers, and other metadata have no source line.
      rows.push({ text, oldLine: null, newLine: null });
    }
  }

  return rows;
}

export function findSatdLocation(
  finding: SatdAnalysisResponse,
  files: PullRequestFile[]
): { file: PullRequestFile; rowIndex: number } | null {
  if (finding.label !== 'SATD' || !Number.isInteger(finding.lineNumber) || finding.lineNumber < 1) {
    return null;
  }

  const file = files.find(candidate => candidate.filename === finding.filename);
  if (!file) return null;

  const rowIndex = parseUnifiedDiff(file.patch).findIndex(row => row.newLine === finding.lineNumber);
  return rowIndex < 0 ? null : { file, rowIndex };
}

export function isSatdRow(
  row: DiffRow,
  filename: string,
  findings: SatdAnalysisResponse[]
): boolean {
  return row.newLine !== null && findings.some(finding =>
    finding.label === 'SATD' && finding.filename === filename && finding.lineNumber === row.newLine);
}
