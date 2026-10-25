# Portable local adaptation; NOT_RUN until owner output.
"""Future exact new-ID target+control Schedule/Attendance fixtures only. No Auth SQL writes."""
import argparse,datetime,json,re
from zoneinfo import ZoneInfo
import runtime
P=runtime.PRIVATE

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
 doc={'_id':'group-native-'+kind+'-'+state['caseId'],'group_id':group,'lesson_id':rows['lessonId'],'semester_id':semester,'student_id':student,'status':'present'}
 uri=(P/'SPRING_DATA_MONGODB_URI').read_text();password=re.search(r':([^:@]+)@',uri).group(1)
 script="db=db.getSiblingDB('attendance_db');if(!db.auth('rct_group_attendance',"+json.dumps(password)+"))throw Error('Scoped auth denied');const wanted="+json.dumps(doc)+";if(db.attendances.countDocuments({_id:wanted._id})!==0)throw Error('Existing own row; stop');db.attendances.insertOne(wanted);print(JSON.stringify({inserted:db.attendances.countDocuments({_id:wanted._id})}));"
 proof=json.loads(runtime.docker('exec','-i',runtime.PROJECT+'-mongo-attendance-1','mongosh','--quiet','--host','127.0.0.1',input=script).stdout);assert proof['inserted']==1
 return rows

def run(go):
 runtime.admission('fixture-rows',go);state=json.loads((P/'fixture-state.json').read_text());assert state['step']=='api-fixture-complete';runtime.private_write(P/'fixture-rows-intent.json',{'caseId':state['caseId'],'sourceCommit':state['sourceCommit']})
 target=seed(state,'target',int(state['groupId']),int(state['subjectId']),int(state['accounts'][0]['id']),2);state.update(target);state['step']='target-rows-ready';(P/'fixture-state.json').write_text(json.dumps(state,indent=2)+'\n')
 control=seed(state,'control',int(state['controlGroupId']),int(state['controlSubjectId']),int(state['controlStudent']['id']),1);state['controlRows']=control;state['step']='owned-fixture-ready';(P/'fixture-state.json').write_text(json.dumps(state,indent=2)+'\n')
 runtime.private_write(P/'fixture-rows-receipt.json',{'target':target,'control':control,'attendanceDocuments':2,'bothClosedPastLessons':True,'foreignGroup1OrExistingAccountsChanged':False});print('Own target/control closed past lesson and real attendance fixtures ready; no delete')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--go',required=True);run(p.parse_args().go)
