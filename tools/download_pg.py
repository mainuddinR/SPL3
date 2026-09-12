import urllib.request
import zipfile
import io
import os
import sys

url = "https://get.enterprisedb.com/postgresql/postgresql-16.2-1-windows-x64-binaries.zip"
dest_dir = r"D:\8th semester\SPL3\tools\pgsql"
os.makedirs(dest_dir, exist_ok=True)

print("Downloading PostgreSQL binaries...")
try:
    response = urllib.request.urlopen(url)
    total_size = int(response.headers.get('content-length', 0))
    downloaded = 0
    chunk_size = 8192 * 16
    data = bytearray()
    
    while True:
        chunk = response.read(chunk_size)
        if not chunk:
            break
        data.extend(chunk)
        downloaded += len(chunk)
        if downloaded % (50 * 1024 * 1024) < chunk_size:
            print(f"Downloaded {downloaded / (1024*1024):.2f} MB / {total_size / (1024*1024):.2f} MB")
            
    print("Extracting...")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        z.extractall(dest_dir)
    print("Done!")
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
