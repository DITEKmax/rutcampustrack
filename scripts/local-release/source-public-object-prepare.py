"""Visible bounded prerequisite for exactly the original three public source objects."""
import subprocess

REVISIONS=('75cb5ed5ae8d731991a5efd447c611b0eb9f65da','4065cda44239af33cd9716273dc8e61d569b5261','753173688d633ca07991f4dd73bdcd2698f45d96')

def main():
    origin=subprocess.check_output(['git','remote','get-url','origin'],text=True,timeout=5).strip()
    if origin!='https://github.com/DITEKmax/rutcampustrack.git':raise ValueError('Exact public source origin required')
    for revision in REVISIONS:
        result=subprocess.run(['git','cat-file','-e',revision+'^{commit}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5)
        if result.returncode:
            print('PREPARE_PUBLIC_SOURCE_OBJECT '+revision,flush=True)
            subprocess.run(['git','fetch','--no-tags','--no-write-fetch-head','--no-recurse-submodules','origin',revision],check=True,timeout=20)
        actual=subprocess.check_output(['git','rev-parse','--verify',revision+'^{commit}'],text=True,timeout=5).strip()
        if actual!=revision:raise ValueError('Public source object mismatch')
    print('PASS original source/migration object prerequisites')

if __name__=='__main__':main()
