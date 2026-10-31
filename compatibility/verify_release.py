"""Verify release contents. Optional baseline files are read only and never redistributed."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import argparse,hashlib,json,re,struct,xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--baseline-jar',type=Path)
parser.add_argument('--previous-api',type=Path)
parser.add_argument('--previous-dungeon-api',type=Path)
args=parser.parse_args()
r=Path(__file__).resolve().parents[1]
v,av='1.19.1','1.3.1'
libs,dist=r/'build/libs',r/'build/distributions'
jar=libs/f'TheMob-{v}-MC1.18-26.3.jar'
no_sqlite=libs/f'TheMob-{v}-MC1.18-26.3-NoSQLite.jar'
api,dungeon=[libs/f'{name}-{av}.jar' for name in ['TheMob-API','TheMob-Dungeon-API']]
source,server,update=[dist/f'TheMob-{v}-MC1.18-26.3-{kind}.zip' for kind in ['Quellprojekt','Serverpaket','Update']]
sdk=dist/f'TheMob-Developer-SDK-{v}.zip'
book=dist/f'TheMob-GitBook-{v}.zip'

def public_members(data):
    pos,cp,i=10,{},1
    count=struct.unpack_from('>H',data,8)[0]
    while i<count:
        tag=data[pos];pos+=1
        if tag==1:
            n=struct.unpack_from('>H',data,pos)[0];pos+=2
            cp[i]=data[pos:pos+n].decode('utf-8',errors='replace');pos+=n
        elif tag in (3,4,9,10,11,12,17,18):pos+=4
        elif tag in (5,6):pos+=8;i+=1
        elif tag in (7,8,16,19,20):pos+=2
        elif tag==15:pos+=3
        else:raise AssertionError(tag)
        i+=1
    pos+=6
    interfaces=struct.unpack_from('>H',data,pos)[0];pos+=2+2*interfaces
    members=set()
    for kind in ['field','method']:
        n=struct.unpack_from('>H',data,pos)[0];pos+=2
        for _ in range(n):
            access,name,desc,attrs=struct.unpack_from('>HHHH',data,pos);pos+=8
            if access&5:members.add((kind,cp[name],cp[desc],access&0x000d))
            for _ in range(attrs):
                length=struct.unpack_from('>I',data,pos+2)[0];pos+=6+length
    return members

totals={}
for task,count in [('test',299),('battlePassApiTest',17),('skilltreeCompatibilityTest',14),('serverProvidersApiTest',8),('standaloneTest',1),('legacy118Test',4),('compatibilityMatrixTest',1),('noSqliteTest',3),('noSqliteLegacy118Test',4)]:
    suites=[ET.parse(p).getroot() for p in (r/'build/test-results'/task).glob('TEST-*.xml')]
    actual=tuple(sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped'])
    assert actual==(count,0,0,0),(task,actual)
    totals[task]=actual[0]
matrix=(r/'build/reports/compatibility/api-matrix.txt').read_text(encoding='utf-8')
assert len(matrix.splitlines())==32
assert all(line.endswith('0 unresolved') for line in matrix.splitlines())
for previous,current in [(args.previous_api,api),(args.previous_dungeon_api,dungeon)]:
    if previous:
        with ZipFile(previous) as old,ZipFile(current) as new:
            for name in old.namelist():
                if name.endswith('.class'):assert public_members(old.read(name))<=public_members(new.read(name)),name

for artifact in [jar,no_sqlite]:
    with ZipFile(artifact) as z:
        names=set(z.namelist())
        assert not {'stats.yml','skills.yml','org/plugin/theMob/core/TheMobSkills.class','org/plugin/theMob/command/StatsCommand.class'}&names
        assert not any(n.startswith(('org/plugin/theMob/player/stats/','org/plugin/theMob/progression/')) for n in names)
        assert 'abilities.yml' in names
        descriptor=z.read('plugin.yml').decode()
        assert '\n  stats:' not in descriptor and 'themob.stats' not in descriptor and 'SkilltreePlugin' not in descriptor
        if artifact==no_sqlite:
            assert not any(n.startswith('org/sqlite/') for n in names)
            assert z.read('themob-build.properties')==b'sqlite.enabled=false\n'
            assert 'META-INF/services/java.sql.Driver' not in names

forbidden=('org/bukkit/','io/papermc/','com/destroystokyo/','net/kyori/','org/junit/','org/mockito/','org/mockbukkit/','be/seeseemelk/','org/slf4j/','kr/toxicity/','me/clip/','net/citizensnpcs/','com/gamingmesh/','me/pikamug/','com/sk89q/','net/advancedplugins/','io/github/battlepass/','com/j256/','ch/sasch/skilltree/','net/luckperms/','dev/aurelium/','me/PM2/','me/gypopo/','net/Indyuce/','io/lumine/','me/angeschossen/','com/palmergames/','com/gmail/nossr50/','me/ryanhamshire/','eu/decentsoftware/','ch/sasch/pk/')
with ZipFile(jar) as z:
    names=set(z.namelist())
    assert not any(name.startswith(forbidden) for name in names)
    assert f'version: "{v}"' in z.read('plugin.yml').decode()
    assert 'api-version: "1.18"' in z.read('plugin.yml').decode()
    assert 'org/sqlite/JDBC.class' in names
    core=[name for name in names if name.startswith('org/plugin/theMob/') and '/libs/' not in name and name.endswith('.class')]
    assert all(int.from_bytes(z.read(name)[6:8],'big')==61 for name in core)
    if args.baseline_jar:
        with ZipFile(args.baseline_jar) as old:
            mobs=[n for n in old.namelist() if n.startswith('mobs/') and n.endswith('.yml')]
            vendor=[n for n in old.namelist() if n.startswith('org/plugin/theMob/libs/') and n.endswith('.class')]
            assert len(mobs)==8 and len(vendor)==14
            for name in mobs+vendor:assert z.read(name)==old.read(name),name
    for file,count in [(api,19),(dungeon,4)]:
        with ZipFile(file) as a:
            classes=[n for n in a.namelist() if n.endswith('.class')]
            assert len(classes)==count
            assert all(n.startswith('org/plugin/theMob/api/') and '/internal/' not in n for n in classes)
            for name in classes:assert a.read(name)==z.read(name)
            assert a.read('META-INF/LICENSE.txt')==(r/'API-LICENSE.txt').read_bytes()

with ZipFile(source) as z:
    names=set(z.namelist())
    assert {'gradlew.bat','gradle/wrapper/gradle-wrapper.jar','settings.gradle.kts','build.gradle.kts','.gitignore','.gitattributes','.gitbook.yaml','GIT-START.md','LICENSE.md','FAEHIGKEITEN.md','docs/SUMMARY.md','compatibility/versions.json','compatibility/fetch_apis.py','src/legacy118/java/org/plugin/theMob/Legacy118SmokeTest.java'}<=names
    assert not any(n.startswith(('build/','.gradle/','.git/','.idea/','compatibility/cache/')) for n in names)
    for file in (r/'src').rglob('*'):
        if file.is_file():assert z.read(file.relative_to(r).as_posix())==file.read_bytes(),file
    assert b'portableSources' not in z.read('build.gradle.kts')
for file in [server,update]:
    with ZipFile(file) as z:
        assert z.read('plugins/TheMob.jar')==jar.read_bytes()
        assert not any(n.startswith('plugins/') and n.endswith('.jar') and n!='plugins/TheMob.jar' for n in z.namelist())
        assert z.read('KOMPATIBILITAET.md')==(r/'KOMPATIBILITAET.md').read_bytes()
for archive in [source,server,update,sdk]:
    with ZipFile(archive) as z:
        assert not any(n.lower().endswith('.jar') and any(p in n.lower() for p in ['battlepass','advancedcrates','economyshopgui','skilltreeplugin']) for n in z.namelist())
with ZipFile(sdk) as s:
    names=set(s.namelist())
    assert {'README.md','API-LICENSE.txt','javadoc/index.html','example/gradlew.bat','example/gradle/wrapper/gradle-wrapper.jar','test-addon/TheMobAddonExample-1.0.0.jar'}<=names
    assert not any('/internal/' in n or n.startswith(('plugins/','.gradle/')) for n in names)
    for prefix in ['TheMob-API','TheMob-Dungeon-API']:
        for suffix in ['','-sources','-javadoc']:
            name=f'{prefix}-{av}{suffix}.jar';assert s.read('api/'+name)==(libs/name).read_bytes()
        assert s.read(f'example/libraries/{prefix}-{av}.jar')==(libs/f'{prefix}-{av}.jar').read_bytes()
    for artifact in ['themob-api','themob-dungeon-api']:
        folder=f'maven-repository/org/plugin/themob/{artifact}/{av}/'
        for name in names:
            if name.startswith(folder) and name.endswith(('.jar','.pom')):
                for alg in ['sha1','sha256','sha512']:assert s.read(name+'.'+alg).decode().strip()==hashlib.new(alg,s.read(name)).hexdigest()
        tree=ET.fromstring(s.read(folder+f'{artifact}-{av}.pom'));ns={'m':'http://maven.apache.org/POM/4.0.0'}
        assert all(n.findtext('m:scope',namespaces=ns)=='provided' for n in tree.findall('m:dependencies/m:dependency',ns))
    with ZipFile(BytesIO(s.read('test-addon/TheMobAddonExample-1.0.0.jar'))) as addon:
        assert 'api-version: \'1.18\'' in addon.read('plugin.yml').decode()
        assert not any(n.startswith('org/plugin/') for n in addon.namelist())
        assert all(int.from_bytes(addon.read(n)[6:8],'big')==61 for n in addon.namelist() if n.endswith('.class'))
with ZipFile(book) as z:
    for file in (r/'docs').rglob('*'):
        if file.is_file():assert z.read(file.relative_to(r).as_posix())==file.read_bytes()

broken=[]
for file in (r/'docs').rglob('*.md'):
    for target in re.findall(r'\]\(([^)]+)\)',file.read_text(encoding='utf-8')):
        target=target.split('#')[0]
        if target and not re.match(r'\w+://|mailto:',target) and not (file.parent/target).exists():broken.append((str(file.relative_to(r)),target))
assert not broken,broken
summary=(r/'docs/SUMMARY.md').read_text(encoding='utf-8')
pages=re.findall(r'\]\(([^)]+)\)',summary)
assert len(pages)==len(set(pages))

report={'version':v,'api':av,'javaBytecode':17,'minecraft':'1.18–26.3','apiVersions':32,'tests':totals,'totalTests':sum(totals.values()),'playerSkillsStatsRemoved':True,'itemBonusesAndEffectsRetained':True,'api13AbiPreserved':bool(args.previous_api and args.previous_dungeon_api),'originalEightMobsAnd14VendorClassesPreserved':bool(args.baseline_jar),'docsPages':len(list((r/'docs').rglob('*.md'))),'artifacts':{}}
for file in [jar,no_sqlite,api,dungeon,source,server,update,sdk,book]:
    digest=hashlib.sha256(file.read_bytes()).hexdigest()
    file.with_suffix(file.suffix+'.sha256').write_text(digest+'  '+file.name+'\n',encoding='ascii')
    report['artifacts'][file.name]={'bytes':file.stat().st_size,'sha256':digest}
(r/f'build/release-{v}-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
