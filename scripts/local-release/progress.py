"""Bounded projection of Gradle native callbacks. No raw log line is public."""
from pathlib import Path
import re
import subprocess
import time

TASKS=frozenset(('compileJava','compileTestJava','test','integrationTest','jacocoTestReport',
                'bootJar','classes','testClasses','processResources','processTestResources',
                'generateProto','generateTestProto'))
OUTCOMES=frozenset(('FAIL','NO_SOURCE','UP_TO_DATE','SKIPPED','SUCCESS'))
PREFIX=b'RCT_GRADLE_V1|'
LIMIT=2048

class Progress:
    def __init__(self,root,clock=time.monotonic):
        self.clock=clock;self.buffer=b'';self.dropping=False;self.task=None;self.current=None
        self.last={};self.last_print=0.;self.totals={}
        self.counts={};self.completed_classes=set();self.tasks=set()
        self.sources=set()
        self.allowed_tasks=set()
        names=subprocess.check_output(['git','ls-files','-z','--','services/**/src/test/java/**/*.java',
                                       'services/**/build.gradle.kts','build.gradle.kts'],cwd=root,
                                      stderr=subprocess.DEVNULL,timeout=15)
        if len(names)>8*1024*1024:raise ValueError('Source identity bound')
        for raw in names.split(b'\0'):
            try:name=raw.decode('ascii')
            except UnicodeError:continue
            if '/src/test/java/' in name and name.endswith('.java'):
                identity=name.split('/src/test/java/',1)[1].removesuffix('.java').replace('/','.')
                if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+',identity):self.sources.add(identity)
            elif name=='build.gradle.kts' or name.endswith('/build.gradle.kts'):
                parts=Path(name).parent.parts
                if any(re.fullmatch('[A-Za-z0-9_-]+',p) is None for p in parts):continue
                project=':'+':'.join(parts)
                for task in TASKS:self.allowed_tasks.add(project.rstrip(':')+':'+task)

    @staticmethod
    def number(raw):
        if re.fullmatch('[0-9]{1,9}',raw) is None:raise ValueError('Count')
        return int(raw)

    @staticmethod
    def service(task):
        parts=task.split(':')
        for name in ('academic','auth','schedule','attendance','notification'):
            if name+'-service' in parts:return name
        if 'mobile-bff' in parts:return 'mobile-bff'
        return parts[-2] or 'root'

    def counters(self,task):
        return self.totals.setdefault(self.service(task),{'unit':[0,0,0],'integration':[0,0,0],'classes':0})

    def feed(self,chunk):
        """Keep at most one bounded line; discard oversized records until newline."""
        events=[]
        for part in chunk.splitlines(keepends=True):
            ended=part.endswith(b'\n')
            if not self.dropping:
                if len(self.buffer)+len(part)>LIMIT:self.buffer=b'';self.dropping=True
                else:self.buffer+=part
            if ended:
                if not self.dropping:
                    event=self.consume(self.buffer.rstrip(b'\r\n'))
                    if event:events.append(event)
                self.buffer=b'';self.dropping=False
        return events

    def consume(self,line):
        if not line.startswith(PREFIX):return None
        try:
            fields=line.decode('ascii').split('|');kind=fields[1];task=fields[2]
            if task not in self.allowed_tasks:return None
            if kind=='TASK_START' and len(fields)==3:
                self.counts[task]=(0,0,0)
                self.completed_classes={entry for entry in self.completed_classes if entry[0]!=task}
                self.task=task;self.current=None
                return self.service(task)+': '+task.rsplit(':',1)[-1]+' начат'
            if kind=='TASK_DONE' and len(fields)==4 and fields[3] in OUTCOMES:
                self.tasks.add(task);self.task=task;self.current=None
                state={'FAIL':'ошибка','NO_SOURCE':'нет исходников','UP_TO_DATE':'актуален',
                       'SKIPPED':'пропущен','SUCCESS':'завершён'}[fields[3]]
                return self.service(task)+': '+task.rsplit(':',1)[-1]+' '+state
            if kind not in {'TEST_START','TEST_DONE','CLASS_DONE'} or task.rsplit(':',1)[-1] not in {'test','integrationTest'}:return None
            source=fields[3]
            if source not in self.sources:return None
            if kind=='TEST_DONE' and len(fields)==7:
                count,failed,skipped=map(self.number,fields[4:])
                old=self.counts.get(task,(0,0,0))
                if any(a<b for a,b in zip((count,failed,skipped),old)) or failed+skipped>count:return None
                bucket=self.counters(task)['integration' if task.endswith(':integrationTest') else 'unit']
                for index,value in enumerate((count,failed,skipped)):bucket[index]+=value-old[index]
                self.counts[task]=(count,failed,skipped);self.last[self.service(task)]=self.clock()
            elif kind=='TEST_START' and len(fields)==5:
                if fields[4]!='NONE':self.number(fields[4])
            elif kind=='CLASS_DONE' and len(fields)==6:
                self.number(fields[4])
                if fields[5]!='NONE':self.number(fields[5])
                if (task,source) not in self.completed_classes:
                    self.completed_classes.add((task,source))
                    if task.endswith(':integrationTest'):self.counters(task)['classes']+=1
                self.task=task;self.current=None
                return self.service(task)+': класс '+source+' завершён; '+self.summary()
            else:return None
            changed=self.current!=source or self.task!=task
            self.current=source;self.task=task
            if changed or self.clock()-self.last_print>=2:return self.summary()
        except (ValueError,UnicodeError,IndexError):return None

    def summary(self):
        self.last_print=self.clock()
        last=self.last.get(self.service(self.task or ':root:test'))
        age='ещё нет' if last is None else str(max(0,int(self.clock()-last)))+' с'
        totals=self.counters(self.task or ':root:test');unit=totals['unit'];integration=totals['integration']
        return (self.service(self.task or ':root:test')+': unit — завершено '+str(unit[0])+
                ' (ошибки '+str(unit[1])+', пропуски '+str(unit[2])+'); integrationTest — '+
                str(totals['classes'])+' классов, '+str(integration[0])+' тестов завершено (ошибки '+
                str(integration[1])+', пропуски '+str(integration[2])+'); текущий класс: '+
                (self.current or 'нет')+'; после последнего завершённого теста: '+age)
