import docx
import sys

def extract_text(filepath):
    try:
        doc = docx.Document(filepath)
        for para in doc.paragraphs:
            print(para.text)
    except Exception as e:
        print(f"Error reading docx: {e}")

if __name__ == "__main__":
    extract_text(r"D:\8th semester\SPL3\SPL-3_Project_Proposal.docx")
