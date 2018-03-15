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
MAX_CLASS_NSAMPLES=2000

class MyHTMLParser(HTMLParser):
	data=[]
	def handle_data(self, data):
		self.data.append(data)

def load_languages():
    for lan,lext in zip(languages,language_extended):
        infiles[lan]=indir+lan+'wiki/'+infilename
        outfiles_parsed[lan]=outdir_parsed+lext+'_short.tsv'
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
	return out


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
	#entity_id	revision_id	imestamp	entity_title	section	start	offset	statement	paragraph	citations
	for line in f:
		row=line[:-1].split('\t')
		label=get_label(row[9])
		text=row[7]
		parser.feed(text)
		sentence=('').join(parser.data)
		cleansent=clean_sentence(sentence)
		parser.data=[]
		unique=hashlib.sha224(cleansent).hexdigest()
		original[unique]=line
		parsed[unique]=('\t').join(row[:7])+'\t'+cleansent+'\t'+str(label)+'\n'
		if len(cleansent.split(' '))>5:
			if label==1:
				positives.append(unique)
			else:
				negatives.append(unique)
	fo_short=open(outfiles_short[lan],'w')
	fo_parsed=open(outfiles_parsed[lan],'w')
	alldata=[]
	shuffle(positives)
	l=max(len(negatives),MAX_CLASS_NSAMPLES)
	alldata=negatives+positives[:len(negatives)]
	for id in alldata:
		fo_short.write(original[id])
		fo_parsed.write(parsed[id])



