"""Source-derived common edge on the new exact synthetic realm, never the old edge."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

DOCKER=['docker','--host=unix:///var/run/docker.sock']

class Common:
    def __init__(self,source,work,revision,nonce,audit):
        self.source,self.work,self.revision,self.nonce,self.audit=source,work,revision,nonce,audit
        self.directory=work/'common';self.directory.mkdir(mode=0o700)
        self.profile=self.directory/'profile.json';self.run_dir=self.directory/'run'
        self.cid=None;self.started=False
        self.profile.write_text(json.dumps({'project':'rct-pr3-common-'+nonce[:12],'network':'rct-pr3-native-'+nonce[:12]+'_private_net','ipv4':'172.31.36.11','httpPort':15440,'httpsPort':15443,'nonce':nonce})+'\n')
        self.gate=['/usr/bin/python3',str(source/'tests/e2e/pr3-nginx/gate.py'),'--local-release-profile',str(self.profile)]

    def call(self,args,seconds=240):
        return subprocess.run(args,cwd=self.source,check=True,timeout=seconds)

    def output(self,args):
        return subprocess.check_output(args,cwd=self.source,text=True,stderr=subprocess.PIPE,timeout=20).strip()

    def prepare_start_check(self):
        # WEB root build is the already completed same-revision normal Vue build. Prefix builds differ.
        self.call(self.gate+['pin','--expected-sha',self.revision,'--source-sha',self.revision,'--app','web','--fixture-mode','false','--dist',str(self.source/'frontends/web-vue/dist'),'--build-log',str(self.audit/'vue.private.log'),'--out',str(self.directory/'web.pin.json')])
        for app in ('pwa','tma'):
            self.call(['/usr/bin/python3',str(self.source/'tests/e2e/pr3-nginx/build.py'),'--expected-sha',self.revision,'--app',app,'--out',str(self.directory/(app+'-dist'))])
        image=self.output(DOCKER+['image','inspect','--format','{{.Id}}','nginx:1.27-alpine'])
        args=self.gate+['prepare','--expected-sha',self.revision,'--image-id',image,'--gateway','api-gateway:8080','--network','rct-pr3-native-'+self.nonce[:12]+'_private_net','--landing-dist',str(self.source/'frontends/landing/dist'),'--out',str(self.run_dir)]
        for app in ('web','pwa','tma'):
            dist=self.source/'frontends/web-vue/dist' if app=='web' else self.directory/(app+'-dist')
            args += ['--'+app+'-dist',str(dist),'--'+app+'-pin',str(self.directory/(app+'.pin.json'))]
        self.call(args)
        private=self.source/'.pr3-local/runtime/private';tls=private/'tls';edge=self.run_dir/'tls'
        self.call(['openssl','req','-newkey','rsa:2048','-nodes','-keyout',str(edge/'privkey.pem'),'-out',str(edge/'server.csr'),'-subj','/CN=localhost'])
        extension=edge/'server.ext';extension.write_text('subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n')
        # Only this new synthetic CA signs the common-edge leaf. No system/browser global trust writes.
        self.call(['openssl','x509','-req','-in',str(edge/'server.csr'),'-CA',str(tls/'ca.crt'),'-CAkey',str(tls/'ca.key'),'-set_serial','2','-days','1','-extfile',str(extension),'-out',str(edge/'server.crt')])
        (edge/'fullchain.pem').write_bytes((edge/'server.crt').read_bytes()+(tls/'ca.crt').read_bytes())
        self.call(['openssl','genpkey','-genparam','-algorithm','DH','-pkeyopt','group:ffdhe2048','-out',str(edge/'dhparam.pem')])
        for file in edge.iterdir():file.chmod(0o600)
        self.compose=DOCKER+['compose','-f',str(self.run_dir/'compose.json')]
        # Pins bind exact .11, network, project, nonroot identity, mounts, image and generated source bytes.
        self.started=True
        self.call(self.compose+['up','-d','--pull','never','--no-build'],seconds=120)
        self.cid=self.output(self.compose+['ps','-q','edge'])
        self.call(self.compose+['exec','-T','edge','nginx','-t'])
        self.call(self.gate+['check','--expected-sha',self.revision,'--run-dir',str(self.run_dir),'--base','https://127.0.0.1:15443','--api-base','https://127.0.0.1:19443','--api-ca',str(tls/'ca.crt'),'--api-ca-sha256',hashlib.sha256((tls/'ca.crt').read_bytes()).hexdigest(),'--out',str(self.directory/'static.safe.json')])
        self.browser_home=self.directory/'browser-home';nss=self.browser_home/'.pki/nssdb';nss.mkdir(mode=0o700,parents=True)
        self.call(['certutil','-N','-d','sql:'+str(nss),'--empty-password'])
        self.call(['certutil','-A','-d','sql:'+str(nss),'-n','own-local-release-ca','-t','C,,','-i',str(tls/'ca.crt')])

    def browser(self):
        env=dict(os.environ);env['RCT_COMMON_BROWSER_HOME']=str(self.browser_home)
        env['NODE_EXTRA_CA_CERTS']=str(self.source/'.pr3-local/runtime/private/tls/ca.crt')
        subprocess.run(['node',str(self.source/'scripts/local-release/common-auth.cjs'),str(self.source),str(self.directory)],cwd=self.source,env=env,check=True,timeout=180)

    def close(self):
        if not self.started:return
        pins=json.loads((self.run_dir/'pins.json').read_text());compose=json.loads((self.run_dir/'compose.json').read_text())
        for name,digest in pins['configs'].items():
            if hashlib.sha256((self.run_dir/name).read_bytes()).hexdigest()!=digest:raise ValueError('Common config changed; preserve own edge')
        current=self.output(self.compose+['ps','-q','--all','edge'])
        if not current:return
        if self.cid is not None and current!=self.cid:raise ValueError('Common edge ID changed')
        row=json.loads(self.output(DOCKER+['inspect','--format','{"id":{{json .Id}},"image":{{json .Image}},"project":{{json (index .Config.Labels "com.docker.compose.project")}},"nonce":{{json (index .Config.Labels "rct.pr3-nginx.run")}}}',current]))
        if row['image']!=pins['imageId'] or row['project']!=compose['name'] or row['nonce']!=pins['runNonce']:raise ValueError('Foreign common edge; HOLD')
        self.call(self.compose+['down'],seconds=120)
        self.started=False
