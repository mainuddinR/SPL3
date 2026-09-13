import zipfile
import xml.etree.ElementTree as ET

def extract_text_from_docx(filepath):
    try:
        with zipfile.ZipFile(filepath) as docx:
            xml_content = docx.read('word/document.xml')
            tree = ET.fromstring(xml_content)
            
            # The namespace for Word XML
            namespace = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
            
            # Find all text nodes
            for node in tree.findall('.//w:t', namespace):
                if node.text:
                    print(node.text)
    except Exception as e:
        print(f"Error reading docx: {e}")

if __name__ == "__main__":
    extract_text_from_docx(r"D:\8th semester\SPL3\SPL-3_Project_Proposal.docx")
