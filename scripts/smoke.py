#!/usr/bin/env python3
"""Integration check against a running nginx + Spring Boot + PostgreSQL stack."""
import datetime, http.cookiejar, json, os, urllib.request, urllib.error, uuid
base=os.environ.get('CHANGOFF_URL','http://localhost:8080')
def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(c,path,method='GET',data=None,status=200):
    request=urllib.request.Request(base+'/api'+path,method=method,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json','X-Requested-With':'changoff'})
    try: response=c.open(request)
    except urllib.error.HTTPError as e: response=e
    payload=response.read()
    assert response.status==status,(path,response.status,payload)
    return json.loads(payload) if payload else None
suffix=uuid.uuid4().hex
one,two=client(),client()
assert call(one,'/health')['status']=='UP'
call(one,'/me',status=401)
for i,c in enumerate([one,two]):
    call(c,'/auth/register','POST',dict(name='Smoke test',email=f'smoke-{suffix}-{i}@example.com',password='integration-test-password',bodyweight=75,standard='male'),201)
assert len(call(one,'/exercises'))==12
lift=call(one,'/lifts','POST',dict(exercise='bench-press',weight=75,reps=1,performedOn=datetime.date.today().isoformat()),201)
assert lift['rank']=='Silver 1'
assert call(two,'/lifts')==[]
call(two,'/lifts/'+lift['id'],'DELETE',status=404)
targets=call(one,'/exercises/bench-press/targets?reps=5')
assert len(targets)==24 and targets[3]['weight']==64.3
call(one,'/lifts/'+lift['id'],'DELETE',status=204)
assert call(one,'/dashboard')[0]['rank']=='Unranked'
call(one,'/auth/logout','POST',status=204)
call(one,'/me',status=401)
print('PASS: registration, sessions, exercises, ranks, targets, ownership, deletion, logout.')
print('Two isolated smoke-test accounts remain in the database; no lifts remain.')
