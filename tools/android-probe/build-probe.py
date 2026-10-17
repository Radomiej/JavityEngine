#!/usr/bin/env python3
"""Build a diagnostic API35 APK from existing engine jars; not a production Android target.

SDK tools, R8, AndroidX and libGDX Android artifacts are explicit local inputs.
No desktop UI/GraalJS/Native FFI code is included.
"""
import argparse,os,pathlib,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--sdk',type=pathlib.Path,required=True);p.add_argument('--r8',type=pathlib.Path,required=True)
p.add_argument('--java-home',type=pathlib.Path,required=True);p.add_argument('--deps',type=pathlib.Path,required=True)
p.add_argument('--backend',type=pathlib.Path,required=True);p.add_argument('--natives',type=pathlib.Path,required=True)
p.add_argument('--output',type=pathlib.Path,required=True)
a=p.parse_args();here=pathlib.Path(__file__).resolve().parent;root=here.parents[1]
out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
tools=a.sdk/'build-tools/36.0.0';android=a.sdk/'platforms/android-35/android.jar';java=a.java_home/'bin/java'
def run(*args):subprocess.run([str(x) for x in args],check=True)
jars=[];resources=[];namespaces=[]
for archive in [a.backend]+sorted(a.deps.glob('*')):
 if archive.suffix=='.jar':jars.append(archive);continue
 if archive.suffix!='.aar':continue
 target=out/'aar'/archive.stem;target.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(archive) as z:
  for f in z.infolist():
   path=pathlib.PurePosixPath(f.filename)
   if path.is_absolute() or '..' in path.parts:raise ValueError('Unsafe AAR member')
   z.extract(f,target)
 if (target/'classes.jar').exists():jars.append(target/'classes.jar')
 manifest=target/'AndroidManifest.xml'
 if manifest.exists():
  namespace=ET.parse(manifest).getroot().get('package')
  if namespace:namespaces.append(namespace)
 if (target/'res').exists():
  resource=target/'resources.zip';run(tools/'aapt2','compile','--dir',target/'res','-o',resource);resources.append(resource)
# All module artifacts must already have been built by the normal Gradle reactor.
for name in ('javity-core','javity-voxel'):
 jars.append(root/name/'build/libs'/f'{name}-0.7.0-SNAPSHOT.jar')
libs=root/'javity-desktop/build/install/javity-desktop/lib'
jars += [libs/'gdx-1.14.2.jar',libs/'gdx-jnigen-loader-2.5.2.jar']
assets=out/'assets/voxels';assets.mkdir(parents=True,exist_ok=True)
shutil.copyfile(root/'javity-desktop/src/main/resources/org/javity/demo/voxels/atlas.png',assets/'atlas.png')
generated=out/'generated';generated.mkdir(exist_ok=True)
command=[tools/'aapt2','link','-I',android,'--manifest',here/'AndroidManifest.xml','--auto-add-overlay','--extra-packages',':'.join(namespaces),'--java',generated,'-A',out/'assets','-o',out/'unsigned.apk']
for resource in resources:command+=['-R',resource]
run(*command)
classes=out/'classes';classes.mkdir(exist_ok=True)
run(a.java_home/'bin/javac','--release','17','-classpath',os.pathsep.join(str(x) for x in [android]+jars),'-d',classes,here/'ProbeActivity.java',*generated.rglob('*.java'))
run(a.java_home/'bin/jar','cf',out/'probe.jar','-C',classes,'.')
dex=out/'dex';dex.mkdir(exist_ok=True)
run(java,'-cp',a.r8,'com.android.tools.r8.D8','--min-api','35','--lib',android,'--output',dex,out/'probe.jar',*jars)
with zipfile.ZipFile(out/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for f in dex.glob('classes*.dex'):z.write(f,f.name)
 # D8 writes classes only. Preserve classpath assets used by libGDX fonts and
 # ModelBatch shaders, as a normal Android Gradle packaging task would do.
 seen=set(z.namelist())
 for jar in jars:
  with zipfile.ZipFile(jar) as library:
   for info in library.infolist():
    name=info.filename
    if info.is_dir() or name.endswith('.class') or name=='META-INF/MANIFEST.MF' or name.endswith(('.SF','.RSA','.DSA')):continue
    if name.startswith('META-INF/'):
     name='META-INF/probe-dependencies/'+jar.stem+'/'+name.removeprefix('META-INF/')
    if name in seen:continue
    z.writestr(name,library.read(info));seen.add(name)
 with zipfile.ZipFile(a.natives) as native:
  for name in native.namelist():
   if name.endswith('.so'):z.writestr('lib/x86_64/'+pathlib.PurePosixPath(name).name,native.read(name))
run(tools/'zipalign','-f','-p','4',out/'unsigned.apk',out/'aligned.apk')
key=out/'debug.keystore'
if not key.exists():run(a.java_home/'bin/keytool','-genkeypair','-keystore',key,'-alias','androiddebugkey','-storepass','android','-keypass','android','-dname','CN=Android Debug,O=Javity,C=PL','-keyalg','RSA','-validity','3650')
# apksigner shell script uses JAVA_HOME; only its standard toolchain selector is set.
run(java,'-jar',tools/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--out',out/'javity-android-probe.apk',out/'aligned.apk')
run(java,'-jar',tools/'lib/apksigner.jar','verify',out/'javity-android-probe.apk')
print(out/'javity-android-probe.apk')
