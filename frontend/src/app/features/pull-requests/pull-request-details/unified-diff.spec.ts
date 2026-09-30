import { PullRequestFile, SatdAnalysisResponse } from '../../../models/pull-request.model';
import { findSatdLocation, isSatdRow, parseUnifiedDiff } from './unified-diff';

describe('unified diff locations', () => {
  const patch = [
    '@@ -10,3 +20,4 @@',
    ' context',
    '-removed',
    '+added',
    '+second added',
    ' next context',
    '\\ No newline at end of file',
    '@@ -30,2 +50,2 @@',
    ' another context',
    '+last added',
    '-last removed'
  ].join('\n');

  const file: PullRequestFile = {
    id: 3, sha: 'sha', filename: 'src/Main.java', status: 'modified',
    additions: 3, deletions: 2, changes: 5, patch
  };
  const finding: SatdAnalysisResponse = {
    filename: file.filename, lineNumber: 22, commentText: '// TODO',
    precedingCode: '', succeedingCode: '', label: 'SATD',
    satdProbability: 0.9, nonSatdProbability: 0.1, confidence: 0.9,
    debtCategory: null, categoryReason: null, categoryRuleVersion: null,
    severityState: 'NOT_ASSESSED', severityScore: null, severityReason: null,
    severityRuleVersion: 'severity-rules-v1', methodLength: null, methodComplexity: null,
    methodMetricsRuleVersion: null, riskEvidence: null, riskEvidenceRuleVersion: null
  };

  it('tracks context, additions, and deletions in a single hunk', () => {
    const rows = parseUnifiedDiff(patch);
    expect([rows[1].oldLine, rows[1].newLine]).toEqual([10, 20]);
    expect([rows[2].oldLine, rows[2].newLine]).toEqual([11, null]);
    expect([rows[3].oldLine, rows[3].newLine]).toEqual([null, 21]);
    expect([rows[4].oldLine, rows[4].newLine]).toEqual([null, 22]);
    expect([rows[5].oldLine, rows[5].newLine]).toEqual([12, 23]);
  });

  it('resets counters for each hunk and ignores no-newline metadata', () => {
    const rows = parseUnifiedDiff(patch);
    expect([rows[6].oldLine, rows[6].newLine]).toEqual([null, null]);
    expect([rows[8].oldLine, rows[8].newLine]).toEqual([30, 50]);
    expect([rows[9].oldLine, rows[9].newLine]).toEqual([null, 51]);
    expect([rows[10].oldLine, rows[10].newLine]).toEqual([31, null]);
  });

  it('maps a SATD finding by exact filename and new-file line', () => {
    expect(findSatdLocation(finding, [file])?.rowIndex).toBe(4);
    expect(findSatdLocation({ ...finding, lineNumber: 51 }, [file])?.rowIndex).toBe(9);
    expect(findSatdLocation({ ...finding, lineNumber: 24 }, [file])).toBeNull();
    expect(findSatdLocation({ ...finding, filename: 'Other.java' }, [file])).toBeNull();
  });

  it('never maps NON-SATD and leaves missing patches unavailable', () => {
    expect(findSatdLocation({ ...finding, label: 'NON-SATD' }, [file])).toBeNull();
    expect(findSatdLocation(finding, [{ ...file, patch: '' }])).toBeNull();
    expect(findSatdLocation(finding, [])).toBeNull();
  });

  it('marks only SATD rows in the matching file and current findings', () => {
    const row = parseUnifiedDiff(patch)[4];
    expect(isSatdRow(row, file.filename, [finding])).toBeTrue();
    expect(isSatdRow(row, file.filename, [{ ...finding, label: 'NON-SATD' }])).toBeFalse();
    expect(isSatdRow(row, 'Other.java', [finding])).toBeFalse();
    expect(isSatdRow(row, file.filename, [])).toBeFalse();
  });
});
