import os
import urllib.request
import sys
import time

URL = "https://zenodo.org/api/records/7757462/files/pentacet_clean_and_load_dump.sql/content"
DEST = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
EXPECTED_SIZE = 12170362955 # 11.33 GB

def download_file(url, dest):
    print(f"Starting robust download of {url}")
    print(f"Destination: {dest}")
    
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    
    retries = 0
    max_retries = 50
    
    while retries < max_retries:
        headers = {}
        mode = 'wb'
        downloaded = 0
        
        if os.path.exists(dest):
            downloaded = os.path.getsize(dest)
            if downloaded >= EXPECTED_SIZE:
                print(f"File is already fully downloaded: {downloaded} bytes.")
                return
            headers['Range'] = f"bytes={downloaded}-"
            mode = 'ab'
            print(f"Resuming from {downloaded} bytes.")
            
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=30) as response:
                total_length = response.headers.get('content-length')
                if total_length:
                    total_length = int(total_length) + downloaded
                else:
                    total_length = EXPECTED_SIZE
                
                print(f"Total expected size: {total_length / (1024*1024*1024):.2f} GB")
                
                with open(dest, mode) as f:
                    while True:
                        chunk = response.read(8192*64)
                        if not chunk:
                            break
                        f.write(chunk)
                        downloaded += len(chunk)
                        retries = 0  # Reset retries on successful read
                        if downloaded % (500 * 1024 * 1024) < (8192*64):
                            print(f"Downloaded {downloaded / (1024*1024*1024):.2f} GB / {total_length / (1024*1024*1024):.2f} GB")
            
            # Verify if complete
            if downloaded >= EXPECTED_SIZE:
                print(f"Download complete: {dest}")
                return
            else:
                print(f"Connection dropped at {downloaded} bytes. Retrying...")
                retries += 1
                time.sleep(2)
        except Exception as e:
            print(f"Download error: {e}. Retrying...")
            retries += 1
            time.sleep(5)
            
    print("Max retries reached. Download failed.")
    sys.exit(1)

if __name__ == "__main__":
    download_file(URL, DEST)

