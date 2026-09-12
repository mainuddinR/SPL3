# PENTACET Small Pilot Report

## 1. Pilot Objective
Verify that PENTACET can produce a clean training subset containing comment + surrounding source code + SATD label.

## 2. Source Database / Schema
PENTACET dump (`pentacet_clean_and_load_dump.sql`). Tables used: `project_main` and `comment_attr`.

## 3. Extraction Method
Streaming extraction using `pg_restore`, matching project names and filtering up to exactly 10,000 records.

## 4. Pilot Sample Size
- **Total Extracted:** 10000

## 5. Class Distribution
- **SATD:** 5000 (50.0%)
- **NON-SATD:** 5000 (50.0%)

## 6. SATD Label Distribution
{
  "SATD": 5000
}

## 7. Missing Data Analysis
- Missing comment: 0
- Missing preceding code: 152
- Missing succeeding code: 0
- Missing project: 0

## 8. Duplicate Analysis
- Exact duplicate rows: 0
- Duplicate comment text: 1539
- Duplicate comment + context: 447

## 9. Source-Code Context Availability
- Comment ONLY: 0 (0.0%)
- Comment + Preceding ONLY: 0 (0.0%)
- Comment + Succeeding ONLY: 152 (1.5%)
- Comment + BOTH: 9848 (98.5%)

## 10. Java Verification
- Java records: 10000
- Non-Java records: 0
- Unknown: 0

## 11. Context Quality Manual Check
**C:** /** Name of metric for time from first message till last message flushed */
**P:** public static final String TIMER_TIME_TO_FIRST_MSG =\n      "time-to-first-message-ms";\n  
**S:** public static final String TIMER_COMMUNICATION_TIME = "communication-time-ms";\n  
---
**C:** // REVISIT: Don't waste these structures.
**P:** n            fLeafListType[curIndex] = nodeCur.type();\n            curIndex++;\n        }\n        
**S:** QName qname = new QName(null, null, null, ((CMAny)nodeCur).getURI());\n            
---
**C:** // Check if the run has enough extra space to fit the last tab\n/** The tab pane */\n/** Tab runs */
**P:**                   prevLastLen = (int)(maxTabHeight*weight*2);\n                }\n\n                
**S:** if (max - end > prevLastLen) {\n\n                    // Insert tab from previous row and shift rest
---
**C:** //"Date",\n/* Should we enable buffering of error streams? */\n//"Accept-Charset",\n//"Accept-Encodi
**P:** "Content-Transfer-Encoding",\n        
**S:** "Host",\n        
---
**C:** // !!! TODO generate some stats ???
**P:** eventHandler.quit();\n                        
**S:** reply(221, "Goodbye.");\n                        
---
**C:** /*\n   * @testName: beanRefGlobalTest\n   * \n   * @assertion: A @Resource annotation on a CDI bean 
**P:** t() throws Fault {\n    TEST_PROPS.setProperty(APITEST, "beanRefAppTest");\n    invoke();\n  }\n\n  
**S:** public void beanRefGlobalTest() throws Fault {\n    TEST_PROPS.setProperty(APITEST, "beanRefGlobalTe
---
**C:** // Get new value\n/*\n * Written by Cliff Click and released to the public domain, as explained at\n
**P:** V = val(kvs, idx); 
**S:** if (V instanceof Prime) {\n        return putIfMatch(topmap, chm.copy_slot_and_check(topmap, kvs, id
---
**C:** // Reset the selections
**P:**                   currLidData = tmpLidData;\n                }\n            }\n        }\n\n        
**S:** int selectedIndex = bottomDataTable.getSelectionIndex();\n\n        
---
**C:** // Action Command
**P:**         }\n            }\n        } else {\n            alarmAlert.select(2);\n        }\n\n        
**S:** Label actionLabel = new Label(alarms, SWT.BOLD);\n        
---
**C:** // TODO Auto-generated method stub
**P:** (final DuplexExpression duplexExpression2) {\n        // TODO Auto-generated method stub\n\n    }\n}
**S:** EOF
---
**C:** // TODO Auto-generated method stub
**P:** rated method stub\n    throw new SQLFeatureNotSupportedException("Method not supported");\n  }\n\n  
**S:** throw new SQLFeatureNotSupportedException("Method not supported");\n  
---
**C:** /* minimum, for leading low loudness */
**P:** gfc.ATH.adjust = 0.01f; 
**S:** gfc.ATH.adjustLimit = 1.0f; 
---
**C:** /* There is actual data in the time series */
**P:** n.addValue(Double.parseDouble(values[count]));\n                count++;\n            }\n        }\n
**S:** String[] values = line.split(" ");\n\n            
---
**C:** /**\n     * {@inheritDoc} <!--workaround-->\n     */
**P:** )));\n        }\n        int opc = opCode(op);\n        throw new AssertionError(op);\n    }\n\n    
**S:** @Override\n    @ForceInline\n    public final\n    VectorMask<Float> test(VectorOperators.Test op,\n
---
**C:** // TODO Auto-generated catch block
**P:** catch (Exception e) {\n\t\t\t// TODO Auto-generated catch block\n\t\t\te.printStackTrace();\n\t\t}\n
**S:** e.printStackTrace();\n\t\t
---
**C:** /**\n\t * MPEG 2.0 slen for intensity stereo.\n\t */
**P:** private int n_slen2[] = new int[512];\n\t
**S:** private int i_slen2[] = new int[256];\n\n\t
---
**C:** // TODO extract the above details from the signing certificate? Reason as a parameter?\n// the signi
**P:** signature.setReason("ΑΚΡΙΒΕΣ ΑΝΤΙΓΡΑΦΟ");\n        
**S:** signature.setSignDate(Calendar.getInstance());\n\n        
---
**C:** // Did we find a stationId in this data?
**P:**             stationId = ((ObStation) loc).getStationId();\n            }\n            \n            
**S:** if(stationId != null) {\n                for(Pattern p : patterns) {\n                    if(p != nu
---
**C:** /*\nDocComment[DOC_COMMENT, pos:1\n  firstSentence: empty\n  body: empty\n  block tags: 1\n    Hidde
**P:** void hidden_text() { }\n
**S:** EOF
---
**C:** /*\n\t\t\t * lame_encode_flush may have set gfc.mf_sample_to_encode to 0 so we\n\t\t\t * have to rei
**P:** assert (gfc.mf_size <= LameInternalFlags.MFSIZE);\n\n\t\t\t
**S:** if (gfc.mf_samples_to_encode < 1) {\n\t\t\t\tgfc.mf_samples_to_encode = Encoder.ENCDELAY + Encoder.P
---

## 12. Project Distribution
- Unique Projects: 1074
- Top 5 Projects:
project_name
author2769_awips2        2467
author1745_jump3r         926
author51_dragonwell17     510
author416_corretto-8      347
author23_openjdk-jdk      329

## 13. Project-Level Split Feasibility
Feasible (1074 distinct projects available in the 10k sample).

## 14. Storage and RAM Usage
- Pilot Dataset Size: 18.91 MB
- Report Size: 0.01 MB

## 15. Time Required
Streaming completed in a few minutes.

## 16. Risks
- Highly duplicated comments.
- Context quality depends on parser logic in original PENTACET generation.

## 17. Recommendation
Use PENTACET for the final CodeBERT training as it provides the exact code context needed.

## 18. Final Verdict

PENTACET SMALL PILOT SUCCESSFUL
