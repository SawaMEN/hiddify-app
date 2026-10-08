#!/usr/bin/env python3
"""Convert original Fluent glyph identities to native vectors from a pinned Microsoft checkout."""
import argparse,subprocess,xml.etree.ElementTree as ET
from pathlib import Path

PIN='cd332ec1f90cda2fad16f86754f8da52f1e9ab30'
ICONS={
 'native_more':('More Vertical','more_vertical',24,'regular'),
 'native_search':('Search','search',24,'regular'),
 'native_outbound_sort':('Arrow Sort','arrow_sort',24,'regular'),
 'native_flash':('Flash','flash',24,'filled'),
 'native_history':('History','history',24,'regular'),
 'native_wifi_signal':('WiFi 1','wifi_1',24,'regular'),
 'native_log_play':('Play','play',20,'regular'),
 'native_log_pause':('Pause','pause',20,'regular'),
 'native_log_clear':('Delete Lines','delete_lines',20,'regular'),
 'native_about_open':('Open','open',24,'regular'),
 'native_sub_upload':('Arrow Upload','arrow_upload',16,'regular'),
 'native_sub_download':('Arrow Download','arrow_download',16,'regular'),
 'native_sub_total':('Arrow Bidirectional Up Down','arrow_bidirectional_up_down',16,'regular'),
 'native_sub_expiry':('Clock Dismiss','clock_dismiss',20,'regular'),
 'native_about_sync':('Arrow Sync','arrow_sync',24,'regular'),
}

def main():
 parser=argparse.ArgumentParser();parser.add_argument('checkout',type=Path);args=parser.parse_args()
 head=subprocess.check_output(['git','-C',str(args.checkout),'rev-parse','HEAD'],text=True).strip()
 if head!=PIN:raise ValueError(f'Expected Microsoft icons at {PIN}, got {head}')
 target=Path('android/app/src/main/res/drawable')
 for resource,(directory,name,size,weight) in ICONS.items():
  source=args.checkout/'assets'/directory/'SVG'/f'ic_fluent_{name}_{size}_{weight}.svg'
  root=ET.parse(source).getroot();assert root.attrib['viewBox']==f'0 0 {size} {size}'
  paths=[]
  for path in root.iter('{http://www.w3.org/2000/svg}path'):
   if set(path.attrib)-{'d','fill','fill-rule','clip-rule'}:raise ValueError(source)
   rule=' android:fillType="evenOdd"' if path.attrib.get('fill-rule')=='evenodd' else ''
   paths.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{path.attrib["d"]}"{rule} />')
  assert paths
  target.joinpath(resource+'.xml').write_text(f'<!-- Microsoft Fluent System Icons, MIT; {name}_{size}_{weight}; source {PIN}. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{size}" android:viewportHeight="{size}">\n'+'\n'.join(paths)+'\n</vector>\n')
 Path('docs/licenses/fluent-system-icons.txt').write_text((args.checkout/'LICENSE').read_text())
 print(f'Vendored {len(ICONS)} Fluent glyphs at {PIN}')

if __name__=='__main__':main()
