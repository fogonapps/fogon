import json,sys
w=json.load(open(sys.argv[1])); k=json.load(open(sys.argv[2])); dif=[]
def cmp(a,b,p):
    if isinstance(a,dict) and isinstance(b,dict):
        ks=set(a)|set(b)
        for x in sorted(ks):
            if x not in a or x not in b:
                # la web omite claves undefined; kotlin pone null
                if (a.get(x) is None) and (b.get(x) is None): continue
                dif.append(f'{p}.{x}: falta en {"web" if x not in a else "kotlin"} ({a.get(x)!r} vs {b.get(x)!r})'); continue
            cmp(a[x],b[x],f'{p}.{x}')
    elif isinstance(a,list) and isinstance(b,list):
        if len(a)!=len(b): dif.append(f'{p}: largo {len(a)} vs {len(b)}'); return
        for i,(x,y) in enumerate(zip(a,b)): cmp(x,y,f'{p}[{i}]')
    elif isinstance(a,(int,float)) and isinstance(b,(int,float)) and not isinstance(a,bool) and not isinstance(b,bool):
        if abs(a-b)>1e-9*max(1,abs(a)): dif.append(f'{p}: {a} vs {b}')
    elif a!=b: dif.append(f'{p}: {a!r} vs {b!r}')
cmp(w,k,'')
print('diferencias:',len(dif)); print('\n'.join(dif[:40]))
