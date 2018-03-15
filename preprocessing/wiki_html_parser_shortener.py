import sys  
import re 
import hashlib
from HTMLParser import HTMLParser
from random import shuffle
reload(sys)  
sys.setdefaultencoding('utf8')


languages=['en','fr','it']
language_extended=['english','french','italian']
indir='../data_local/html_data/'
infilename='clean_statements.txt'
outdir_parsed='../data_clean/'
outdir_short='../data_short/'
positives={}
infiles={}
outfiles_parsed={}
outfiles_short={}

class MyHTMLParser(HTMLParser):
	data=[]
	def handle_data(self, data):
		self.data.append(data)

def load_languages():
    for lan,lext in zip(languages,language_extended):
        infiles[lan]=indir+lan+'wiki/'+infilename
        outfiles_parsed[lan]=outdir_parsed+lext+'.tsv'
        outfiles_short[lan]=outdir_short+lext+'.tsv'

def clean_sentence(sentence):
	cleansent=sentence.replace('\\n', '')
	cleansent=cleansent.replace('\\r\\n', '')
	cleansent=re.sub(' +',' ',cleansent)
	return cleansent	

def get_label(field):
	if field=='N/A':
		out=0
	else:
		out=1


parser =  MyHTMLParser()
load_languages()

for lan in languages:
	positives=[]
	negatives=[]
	original={}
	parsed={}
	f=open(infiles[lan],'rU')
	f.readline()
	oldline=''
	#entity_id	revision_id	entity_title	section	start	offset	statement	paragraph	citations
	for line in f:
	#	if len(oldline)>0:
	#		print 'XXXXXXXXXX'
	#		print line
	#		print '************'
	#		print '+++'+oldline+'+++'
	#		line=oldline[:-1]+line
		row=line[:-1].split('\t')
	#	if len(row)<9:#
	#		oldline=line
	#		continue
		label=get_label(row[8])
	#	oldline=''
		text=row[6]
		parser.feed(text)
		sentence=('').join(parser.data)
		cleansent=clean_sentence(sentence)
		parser.data=[]
		unique=hashlib.sha224(cleansent).hexdigest()
		original[unique]=line
		parsed[unique]=('\t').join(row[:6])+'\t'+cleansent+'\t'+str(label)+'\n'
	#	try:
	#		check[unique]+=1
	#	except:
	#		check[unique]=1
		if len(cleansent.split(' '))>5:
			if label==1:
				positives.append(unique)
			else:
				negatives.append(unique)
	fo_short=open(outfiles_short[lan],'w')
	fo_parsed=open(outfiles_parsed[lan],'w')
	alldata=[]
	shuffle(positives)
	alldata=negatives+positives[:len(negatives)]
	for id in alldata:
		fo_short.write(original[id])
		fo_parsed.write(parsed[id])



