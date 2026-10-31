# Portable local adaptation; NOT_RUN until owner output.
"""Future exact new-ID target+control Schedule/Attendance fixtures only. No Auth SQL writes."""
import argparse,datetime,json,re
from zoneinfo import ZoneInfo
import runtime
P=runtime.PRIVATE


SCHEMA='rct.fixture-attendance.v1'

def attendance_javascript(kind,doc,password):
 parameters=json.dumps({'schema':SCHEMA,'caseId':kind,'doc':doc,'password':password})
 return 'const p='+parameters+';\n'+r"""
async function main(){
 const out={schema:p.schema,caseId:p.caseId,ok:false,stage:'AUTH',checks:{},codes:{}};
 try{
  const d=db.getSiblingDB('attendance_db');
  const auth=await d.auth('rct_group_attendance',p.password);
  if(!(auth===1||auth===true||(auth&&auth.ok===1)))throw Error('Scoped auth denied');
  out.checks.ownAuthenticated=true;out.stage='ABSENCE';
  const wanted={...p.doc};
  for(const key of ['group_id','lesson_id','semester_id','user_id','subject_id','marked_by'])wanted[key]=NumberLong(String(wanted[key]));
  wanted.lesson_date=new Date(wanted.lesson_date+'T00:00:00.000Z');
  wanted.created_at=new Date(wanted.created_at);wanted.updated_at=new Date(wanted.updated_at);
  const c=d.attendances;
  const count=await c.countDocuments({$or:[{_id:wanted._id},{lesson_id:wanted.lesson_id,user_id:wanted.user_id}]},{maxTimeMS:10000});
  if(count!==0)throw Error('Existing fixture attendance; no replay');
  out.checks.absentBefore=true;out.stage='INSERT';
  const inserted=await c.insertOne(wanted);
  if(inserted.acknowledged!==true)throw Error('Insert acknowledgement unknown');
  out.checks.insertAcknowledged=true;out.stage='VERIFY';
  const after=await c.countDocuments({_id:wanted._id,lesson_id:wanted.lesson_id,user_id:wanted.user_id,group_id:wanted.group_id,subject_id:wanted.subject_id,semester_id:wanted.semester_id,lesson_date:wanted.lesson_date,lesson_number:1,status:'present',source:'headman',marked_by:wanted.marked_by},{maxTimeMS:10000});
  if(after!==1)throw Error('Own attendance verification failed');
  out.checks.exactRowVerified=true;out.stage='DONE';out.ok=true;print(JSON.stringify(out));
 }catch(e){
  if(Number.isInteger(e&&e.code))out.codes.mongo=e.code;
  print(JSON.stringify(out));quit(2);
 }
}
main();
"""

def attendance_result(stdout,process_exit,kind):
 try:
  row=json.loads(stdout)
  assert type(row) is dict and set(row)=={'schema','caseId','ok','stage','checks','codes'}
  assert row['schema']==SCHEMA and row['caseId']==kind and type(row['ok']) is bool
  assert row['stage'] in ['AUTH','ABSENCE','INSERT','VERIFY','DONE']
  assert type(row['checks']) is dict and set(row['checks'])<={'ownAuthenticated','absentBefore','insertAcknowledged','exactRowVerified'}
  assert all(value is True for value in row['checks'].values())
  assert type(row['codes']) is dict and set(row['codes'])<={'mongo'} and all(type(code) is int for code in row['codes'].values())
 except Exception:
  return {'schema':SCHEMA,'caseId':kind,'ok':False,'stage':'PROTOCOL_NON_JSON_OR_SCHEMA','checks':{},'codes':{'processExit':process_exit}}
 row['codes']['processExit']=process_exit
 row['ok']=row['ok'] is True and process_exit==0 and row['stage']=='DONE' and set(row['checks'])=={'ownAuthenticated','absentBefore','insertAcknowledged','exactRowVerified'}
 return row

def attendance_seed(state,kind,group,subject,student,rows,date):
 now=datetime.datetime.now(datetime.timezone.utc).isoformat()
 doc={'_id':'group-native-'+kind+'-'+state['caseId'],'group_id':group,'lesson_id':rows['lessonId'],'semester_id':int(state['semester']['id']),'user_id':student,'subject_id':subject,'lesson_number':1,'lesson_date':date.isoformat(),'status':'present','source':'headman','marked_by':student,'created_at':now,'updated_at':now,'mark_revision':'fixture-'+state['caseId']+'-'+kind}
 runtime.private_write(P/('fixture-'+kind+'-attendance-intent.json'),{'kind':kind,'caseId':state['caseId'],'documentId':doc['_id'],'group':group,'subject':subject,'student':student,'lesson':rows['lessonId'],'scheduleAcknowledgement':'schedule-'+kind+'-fixture.json','automaticRetryAllowed':False})
 uri=(P/'SPRING_DATA_MONGODB_URI').read_text();password=re.search(r':([^:@]+)@',uri).group(1)
 result=runtime.docker('exec','-i',runtime.PROJECT+'-mongo-attendance-1','mongosh','--norc','--quiet','--host','127.0.0.1','--file','/dev/stdin',input=attendance_javascript(kind,doc,password),expected=None)
 # Raw child stdout/stderr can contain credentials in parser errors; never persist or print them.
 proof=attendance_result(result.stdout,result.returncode,kind)
 runtime.private_write(P/('fixture-'+kind+'-attendance-result.json'),proof)
 assert proof['ok'] is True,'Attendance seed HOLD; inspect only closed diagnostic, preserve intent and Schedule acknowledgement; no retry'

def seed(state,kind,group,subject,student,members):
 semester=int(state['semester']['id']);teacher=int(state['retainedTeacherId']);assert semester>1 and group>1 and subject>0 and student>3 and teacher>3
 owned=json.loads(runtime.pg(f"BEGIN READ ONLY; SELECT json_build_object('members',(SELECT count(*) FROM academic_role_eligibility WHERE group_id={group} AND role='STUDENT'),'assignment',(SELECT id FROM assignments WHERE group_id={group} AND subject_id={subject} AND teacher_id={teacher} AND semester_id={semester} AND lesson_type='lecture')); ROLLBACK;",'academic_db').stdout);assert owned['members']==members and owned['assignment'];assignment=int(owned['assignment'])
 start=datetime.date.fromisoformat(state['validFrom']);until=datetime.date.fromisoformat(state['validUntilExclusive']);date=datetime.datetime.now(ZoneInfo('Europe/Moscow')).date()-datetime.timedelta(days=1);assert start<=date<until;day=date.weekday()+1
 runtime.private_write(P/('fixture-'+kind+'-rows-intent.json'),{'group':group,'subject':subject,'assignment':assignment,'student':student,'date':date.isoformat(),'scope':'NEW own target/control only'})
 sql=f"""BEGIN; SET LOCAL ROLE rct_migrator;
 INSERT INTO schedule_assignment_fences(assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive) VALUES({assignment},{group},{subject},{semester},{teacher},'lecture','{start}','{until}','{until}');
 INSERT INTO schedule_items(assignment_id,group_id,subject_id,semester_id,day_of_week,lesson_number,start_time,end_time,week_type,room) VALUES({assignment},{group},{subject},{semester},{day},1,'08:30','09:50','all','SYNTHETIC-GROUP') RETURNING id AS item_id \\gset
 INSERT INTO lesson_occurrences(schedule_item_id,occurrence_date,assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type) VALUES(:item_id,'{date}',{assignment},{group},{subject},{semester},{teacher},'lecture') RETURNING id AS occurrence_id \\gset
 INSERT INTO lessons(schedule_item_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type,lesson_number,day_of_week,week_type_snapshot,start_time,end_time,generation,revision,date,status,is_geo_blocked) VALUES(:item_id,:occurrence_id,{assignment},{group},{subject},{semester},{teacher},'lecture',1,{day},'all','08:30','09:50',1,1,'{date}','closed',false) RETURNING id AS lesson_id \\gset
 UPDATE lesson_occurrences SET current_lesson_id=:lesson_id WHERE id=:occurrence_id;
 SELECT json_build_object('scheduleItemId',:item_id,'occurrenceId',:occurrence_id,'lessonId',:lesson_id); COMMIT;
 """
 rows=json.loads(runtime.pg(sql,'schedule_db').stdout);rows['assignmentId']=assignment;runtime.private_write(P/('schedule-'+kind+'-fixture.json'),rows)
 attendance_seed(state,kind,group,subject,student,rows,date)
 return rows

def run(go):
 runtime.admission('fixture-rows',go);state=json.loads((P/'fixture-state.json').read_text());assert state['step']=='api-fixture-complete';runtime.private_write(P/'fixture-rows-intent.json',{'caseId':state['caseId'],'sourceCommit':state['sourceCommit']})
 target=seed(state,'target',int(state['groupId']),int(state['subjectId']),int(state['accounts'][0]['id']),2);state.update(target);state['step']='target-rows-ready';(P/'fixture-state.json').write_text(json.dumps(state,indent=2)+'\n')
 control=seed(state,'control',int(state['controlGroupId']),int(state['controlSubjectId']),int(state['controlStudent']['id']),1);state['controlRows']=control;state['step']='owned-fixture-ready';(P/'fixture-state.json').write_text(json.dumps(state,indent=2)+'\n')
 runtime.private_write(P/'fixture-rows-receipt.json',{'target':target,'control':control,'attendanceDocuments':2,'bothClosedPastLessons':True,'foreignGroup1OrExistingAccountsChanged':False});print('Own target/control closed past lesson and real attendance fixtures ready; no delete')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--go',required=True);run(p.parse_args().go)
