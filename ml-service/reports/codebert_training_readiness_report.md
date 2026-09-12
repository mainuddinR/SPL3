# CodeBERT Training Pipeline Readiness Report

## 1. Final Dataset Inspection
- **Train rows:** 24006
- **Validation rows:** 3006
- **Test rows:** 2988
- **Columns Available:** comment_id, project_id, project_name, project_language, comment_content, cleaned_comment, comment_line_no, comment_source_file, comment_preceding_code, comment_succeeding_code, satd_affliction, satd_feature

## 2. Required Fields Verified
- Project Name/ID: `project_id`, `project_name`
- Comment Text: `comment_content`, `cleaned_comment`
- Context: `comment_preceding_code`, `comment_succeeding_code`
- Labels: `satd_affliction`, `satd_feature`
- Source File: `comment_source_file`

## 3. Binary Target Extraction
**Recommended Label Mapping:**
- **SATD (1):** When `satd_affliction` is NOT empty.
- **NON-SATD (0):** When `satd_affliction` IS empty.

## 4. 20 Representative Records
**[NON-SATD]** C: `// Enable level saving again` | P: `statesChunkControl.addPlayersToChunkMap();\n\n\t\t` | S: `for (WorldServer world : server.worlds) {\n\t\t\tw`
**[NON-SATD]** C: `/*\n     * (non-Javadoc)\n     * \n     * @see org.eclipse.jface.action.IMenuCreator#dispose()\n    ` | P: `  }\n        menu.setVisible(true);\n    }\n\n    ` | S: `@Override\n    public void dispose() {\n        if`
**[NON-SATD]** C: `/**\n             * Combine all of the options that have been set and return a new {@link Action}\n ` | P: `\n                }\n            }\n\n            ` | S: `@NonNull\n            public Action build() {\n   `
**[NON-SATD]** C: `/*\n         * So one Thursday, I was trying to upgrade this project from the ucar\n         * bufr ` | P: `nMemory(location, newData);\n        }\n    }\n\n}` | S: `try (RandomAccessFile raf = RandomAccessFile.acqui`
**[NON-SATD]** C: `/**\n     * Y coordinate of the 2 value.\n     */` | P: `rd1 = graphRect.height + graphRect.y - 60;\n\n    ` | S: `private int yCoord2 = graphRect.height + graphRect`
**[NON-SATD]** C: `/**\n     * @return the name\n     */` | P: ` type) {\n        this.type = type;\n    }\n\n    ` | S: `public String getName() {\n        return name;\n `
**[NON-SATD]** C: `// Update the vtec start time` | P: `\n            return product;\n        }\n        ` | S: `if ("NEW".equals(vtecObj.getAction())) {\n        `
**[NON-SATD]** C: `// East` | P: `vasHeight / 2 - fontHeight / 2, true);\n\n        ` | S: `gc.drawString("E", canvasWidth - dialXYVal + dashL`
**[NON-SATD]** C: `// Too cold` | P: `   returnValue = FOG_THREAT.GRAY;\n            }\n` | S: `returnValue = FOG_THREAT.GRAY;\n            `
**[NON-SATD]** C: `// find a key match` | P: `editArea.extremaOfSetBits(ll, ur);\n\n        ` | S: `byte dByte = 0;\n        `
**[SATD]** C: `/**\n\t * Set tag <tt>tag</tt> on the set of logs <tt>logIds</tt> and remove it\n\t * from all other` | P: `ag set(TagBuilder tag) throws OlogException;\n\n\t` | S: `public Tag set(TagBuilder tag, Collection<Long> lo`
**[SATD]** C: `//msyms depends on the unnamed module, for which we generally don't know\n// This class does not hav` | P: `        }\n\n            return pack;\n        }\n` | S: `PackageSymbol unnamedPack = getPackage(unnamedModu`
**[SATD]** C: `/*\n                 * Null check: work around GATK issue in which some biallelic sites are missing ` | P: `alleleDepths = genotype.getAD();\n                ` | S: `if (alleleDepths != null) {\n                    f`
**[SATD]** C: `// copying response headers to make sure SESSIONID or other Cookie which comes from remote server\n/` | P: `    status code: {}", statusCode);\n\n            ` | S: `LOGGER.debug("CELLAR HTTP BALANCER:     copying re`
**[SATD]** C: `// Is this test on UDP?` | P: `parseInt(matcher.group(3));\n        }\n    }\n}\n` | S: `static boolean udp;\n\n    `
**[SATD]** C: `// change file:///abc to file:/abc\n//\n// ValidationContext implementation\n//\n//\n// this object ` | P: `stemId = "file:/"+documentSystemId.substring(8);\n` | S: `\n  documentSystemId = "file:/"+documentSystemId.s`
**[SATD]** C: `// is this an overridable java.lang.Object method?` | P: `eMethodName(clazz.getSuperclass());\n    }\n\n    ` | S: `private static boolean isOverridableObjectMethod(f`
**[SATD]** C: `// skip composite (base+cc) for now` | P: `if (e.cp2 != 0)\n                continue;  ` | S: `inByte = e.bb;\n            `
**[SATD]** C: `//if there is only one index found, no need to do intersect, but just a regular non-covering plan\n/` | P: `sable index");\n        return;\n      }\n\n      ` | S: `if(indexInfoMap.size() > 1) {\n        logger.info`
**[SATD]** C: `// FIXME: need to figure out whether we have to align object sizes\n// ourselves (see below)` | P: `, listener, "resolving array element type");\n    ` | S: `if (!((BasicType) elementType).isLazy()) {\n      `

## 5. Length Distribution (Character Count)

| Metric | Comment | Preceding | Succeeding | Combined Input |
|---|---|---|---|---|
| **Min** | 2 | 0 | 0 | 10 |
| **Max** | 334714 | 108610 | 292825 | 336498 |
| **Average** | 562 | 453 | 644 | 1660 |
| **50th Percentile** | 65 | 97 | 72 | 387 |
| **90th Percentile** | 1164 | 751 | 623 | 3328 |
| **95th Percentile** | 2310 | 1533 | 1617 | 6283 |
| **99th Percentile** | 8397 | 6501 | 11091 | 20480 |

*(Note: CodeBERT's absolute max limit is 512 subword tokens, which typically equates to ~1,500-2,000 characters depending on code density).*

## 6. Recommended CodeBERT Input Format & Truncation
**Format:** `<s> {COMMENT} </s></s> {PRECEDING CODE} {SUCCEEDING CODE} </s>`
**Truncation Strategy (to fit 512 tokens):**
1. **Comment:** Do not truncate. Prioritize full inclusion.
2. **Context Split:** Allocate the remaining token budget equally (50/50) between preceding and succeeding code.
3. **Preceding Truncation:** Keep the *bottom* of the preceding code (the lines directly above the comment). Truncate the top.
4. **Succeeding Truncation:** Keep the *top* of the succeeding code (the lines directly below the comment). Truncate the bottom.

## 7. Split Verification
- Expected Columns ONLY: **PASS**
- No Empty Labels: **PASS**
- No Exact Duplicate Rows: **PASS**
- No Project Overlap across splits: **PASS** (0 overlaps)
- No Comment+Context Overlap across splits: **PASS** (0 overlaps)
- Both SATD & NON-SATD present in all splits: **PASS**

## 8. Required Preprocessing Steps
1. **Whitespace Normalization:** Collapse multiple spaces, tabs, and newlines into single spaces to conserve tokens.
2. **Null Handling:** Convert `NaN` or `None` in context columns to empty strings `""`.
3. **Label Encoding:** Map empty `satd_affliction` to `0` and non-empty to `1`.
4. **Tokenization:** Use `RobertaTokenizer.from_pretrained("microsoft/codebert-base")` with `truncation=True` and `max_length=512`.

## 9. Recommended Training Configuration (Colab T4 GPU)
- **Model:** `microsoft/codebert-base`
- **Sequence Length:** 512
- **Batch Size:** 8 (due to 512 seq length and 16GB GPU VRAM)
- **Gradient Accumulation:** 4 (Effective batch size = 32)
- **Learning Rate:** 2e-5 with linear scheduler
- **Epochs:** 3 to 5 (Monitor validation loss)
- **Evaluation Strategy:** Evaluate at the end of every epoch.
- **Early Stopping:** Patience of 2 epochs based on validation F1 score.
- **Random Seed:** 42
- **Metrics:** F1-score (macro and binary), Precision, Recall, Accuracy.

## 10. Final Status

STATUS:
NOT READY FOR CODEBERT TRAINING
(Reason: Validation checks failed on splits or labels.)
