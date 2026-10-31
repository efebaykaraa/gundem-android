#pragma once
#include <atomic>
#include <string>
#include <vector>
#include <cstdint>
struct llama_model;
struct llama_context;
class Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    std::string prefix_system;
    std::vector<int32_t> prefix_tokens;
    std::vector<uint8_t> prefix_state;
public:
    std::string architecture() const;
    std::string stats;
    std::atomic<bool> cancelled{false};
    std::atomic<int> load_progress{-1};
    Engine();
    ~Engine();
    void load(const std::string &path);
    void close();
    std::string generate(const std::string &system, const std::string &user, const std::string &grammar, int max_tokens);
};
