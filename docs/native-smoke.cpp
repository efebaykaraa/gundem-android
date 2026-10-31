#include "engine.h"
#include <fstream>
#include <iostream>
#include <sstream>
std::string read(const std::string &p) { std::ifstream f(p);std::stringstream b;b<<f.rdbuf();return b.str(); }
int main(int argc,char **argv) {
    if(argc!=3) return 2;
    try {
        Engine e;e.load(argv[1]);std::string dir=argv[2];
        std::cout<<"CLASSIFY\n"<<e.generate(read(dir+"/classify-json.txt"),"İşçiler ödenmeyen ücretleri için bugün greve başladı",read(dir+"/classify.gbnf"),160)<<std::endl;
        std::cout<<"CUT\n"<<e.generate(read(dir+"/classify-json.txt"),"İşçiler grevde",read(dir+"/classify.gbnf"),1)<<std::endl;
    } catch(const std::exception &e) { std::cerr<<e.what()<<std::endl;return 1; }
}
