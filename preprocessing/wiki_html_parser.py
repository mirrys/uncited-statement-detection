import sys  
import re 
import hashlib
reload(sys)  
sys.setdefaultencoding('utf8')
check={}
#entity_id	revision_id	entity_title	section	start	offset	statement	paragraph	citations
infile='/home/miriam/Documents/CitationNeeded/uncited-statement-detection/data_local/html_data/itwiki/clean_statements.txt'
outfile='/home/miriam/Documents/CitationNeeded/uncited-statement-detection/data_clean/italian.tsv'
from HTMLParser import HTMLParser
parser=HTMLParser()

class MyHTMLParser(HTMLParser):
	data=[]
#	def handle_starttag(self, tag, attrs):
#		print("Encountered a start tag:", tag)
#	def handle_endtag(self, tag):
#		print("Encountered an end tag :", tag)
	def handle_data(self, data):
		self.data.append(data)


f=open(infile,'rU')
f.readline()
parser =  MyHTMLParser()

count=0
tot=0
fo=open(outfile,'w')
oldline=''
for line in f:
	if len(oldline)>0:
#		print 'XXXXXXXXXX'
#		print line
#		print '************'
#		print '+++'+oldline+'+++'
		line=oldline[:-1]+line
	row=line[:-1].split('\t')
	if len(row)<9:
		oldline=line
		continue
	oldline=''
	sent=row[6]
	parser.feed(sent)
	cleansent=('').join(parser.data)
	parser.data=[]
	if row[8]=='N/A':
		out=0
		count+=1
	else:
		out=1
	cleansent=cleansent.replace('\\n', '')
	cleansent=cleansent.replace('\\r\\n', '')
	cleansent=re.sub(' +',' ',cleansent)
	try:
		unique=hashlib.sha224(line).hexdigest()
		check[unique]+=1
    except:
		check[unique]=1
		if len(cleansent.split(' '))>5:
			outsent=('\t').join(row[:6])+'\t'+cleansent+'\t'+str(out)+'\n'
			fo.write(outsent)
			tot+=1


	###add parsing the last one
